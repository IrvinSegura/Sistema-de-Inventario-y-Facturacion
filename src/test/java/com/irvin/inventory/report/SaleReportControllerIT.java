package com.irvin.inventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.irvin.inventory.AbstractIntegrationTest;
import com.irvin.inventory.config.StoreProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// The database is shared with other tests, so every assertion is scoped to a fresh seller.
class SaleReportControllerIT extends AbstractIntegrationTest {

    @Autowired
    private StoreProperties storeProperties;

    @Test
    void reportAggregatesCompletedSalesAndCountsCancelledSeparately() throws Exception {
        CreatedUser seller = createSeller();
        String sellerToken = sellerToken(seller);
        Long soda = createPricedProduct("10.03", "0.16");
        Long rice = createPricedProduct("25.5", "0");
        addStock(soda, 10);
        addStock(rice, 10);
        // 3 x 10.03 = 30.09 + tax 4.81; 2 x 25.50 = 51.00 -> total 85.90.
        sell(twoLineBody("CASH", soda, 3, rice, 2), sellerToken).andExpect(status().isCreated());
        sell(oneLineBody("CARD", rice, 2), sellerToken).andExpect(status().isCreated());
        Long cancelled = readId(sell(oneLineBody("CASH", soda, 1), sellerToken).andExpect(status().isCreated()));
        postJson("/api/v1/sales/" + cancelled + "/cancellation", """
                {"reason": "Wrong product"}
                """, adminAccessToken()).andExpect(status().isOk());

        // A seller's sellerId filter is ignored: the report is always their own.
        String body = getJson(reportUrl(today().minusDays(1), today().plusDays(1)) + "&sellerId=999999", sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("America/Mexico_City"))
                .andExpect(jsonPath("$.completed.saleCount").value(2))
                .andExpect(jsonPath("$.cancelled.saleCount").value(1))
                .andExpect(jsonPath("$.byPaymentMethod.length()").value(2))
                .andExpect(jsonPath("$.byPaymentMethod[0].paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.byPaymentMethod[1].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.bySeller.length()").value(1))
                .andExpect(jsonPath("$.bySeller[0].sellerId").value(seller.id()))
                .andExpect(jsonPath("$.topProducts[0].productId").value(rice))
                .andExpect(jsonPath("$.topProducts[0].quantity").value(4))
                .andExpect(jsonPath("$.topProducts[1].productId").value(soda))
                .andExpect(jsonPath("$.topProducts[1].quantity").value(3))
                .andExpect(jsonPath("$.byDay.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        // Exact JSON text: money always has two decimals.
        assertThat(body).contains(
                "\"completed\":{\"saleCount\":2,\"subtotal\":132.09,\"tax\":4.81,\"total\":136.90}",
                "\"cancelled\":{\"saleCount\":1,\"subtotal\":10.03,\"tax\":1.60,\"total\":11.63}",
                "\"averageTicket\":68.45",
                "{\"paymentMethod\":\"CARD\",\"saleCount\":1,\"total\":51.00}",
                "\"quantity\":4,\"subtotal\":102.00");
        // Sum, not positions: the sales may straddle midnight in the store's time zone.
        List<Integer> dailyCounts = JsonPath.read(body, "$.byDay[*].saleCount");
        assertThat(dailyCounts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
    }

    @Test
    void adminFiltersBySellerOrSeesEverySeller() throws Exception {
        String adminToken = adminAccessToken();
        CreatedUser seller = createSeller();
        String sellerToken = sellerToken(seller);
        Long product = createPricedProduct("7.5", "0");
        addStock(product, 2);
        sell(oneLineBody("TRANSFER", product, 2), sellerToken).andExpect(status().isCreated());
        String url = reportUrl(today().minusDays(1), today().plusDays(1));

        getJson(url + "&sellerId=" + seller.id(), adminToken)
                .andExpect(jsonPath("$.completed.saleCount").value(1))
                .andExpect(jsonPath("$.completed.total").value(15.0))
                .andExpect(jsonPath("$.bySeller.length()").value(1));
        getJson(url, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bySeller[?(@.sellerId == " + seller.id() + ")].total").value(15.0));
    }

    @Test
    void sellerNeverSeesAnotherSellersSales() throws Exception {
        CreatedUser sellerA = createSeller();
        CreatedUser sellerB = createSeller();
        String tokenA = sellerToken(sellerA);
        String tokenB = sellerToken(sellerB);
        Long onlyA = createPricedProduct("3", "0");
        Long onlyB = createPricedProduct("4", "0");
        addStock(onlyA, 1);
        addStock(onlyB, 1);
        sell(oneLineBody("CASH", onlyA, 1), tokenA).andExpect(status().isCreated());
        sell(oneLineBody("CASH", onlyB, 1), tokenB).andExpect(status().isCreated());
        String url = reportUrl(today().minusDays(1), today().plusDays(1));

        // Without a filter and with another seller's id, B only gets B's sales.
        for (String request : List.of(url, url + "&sellerId=" + sellerA.id())) {
            getJson(request, tokenB)
                    .andExpect(jsonPath("$.completed.saleCount").value(1))
                    .andExpect(jsonPath("$.bySeller.length()").value(1))
                    .andExpect(jsonPath("$.bySeller[0].sellerId").value(sellerB.id()))
                    .andExpect(jsonPath("$.topProducts.length()").value(1))
                    .andExpect(jsonPath("$.topProducts[0].productId").value(onlyB));
        }
    }

    @Test
    void totalsAndBreakdownsAgreeWhileSalesComeIn() throws Exception {
        String token = sellerToken();
        Long product = createPricedProduct("2.5", "0.16");
        addStock(product, 20);
        String url = reportUrl(today().minusDays(1), today().plusDays(1));
        int sales = 8;
        int reports = 6;

        // All queries of a report share one snapshot, so a sale can't be in the totals but not in the breakdowns.
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < sales; i++) {
            String body = oneLineBody(i % 2 == 0 ? "CASH" : "CARD", product, 1);
            UUID key = UUID.randomUUID();
            tasks.add(() -> {
                postJson("/api/v1/sales", body, token, key).andExpect(status().isCreated());
                return null;
            });
        }
        for (int i = 0; i < reports; i++) {
            tasks.add(() -> getJson(url, token).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
        }

        for (String report : runConcurrently(tasks)) {
            if (report == null) {
                continue;
            }
            BigDecimal total = new BigDecimal(JsonPath.read(report, "$.completed.total").toString());
            assertThat(sum(report, "$.byDay[*].total")).isEqualByComparingTo(total);
            assertThat(sum(report, "$.byPaymentMethod[*].total")).isEqualByComparingTo(total);
            assertThat(sum(report, "$.byDay[*].saleCount"))
                    .isEqualByComparingTo(JsonPath.read(report, "$.completed.saleCount").toString());
        }
    }

    @Test
    void daysFollowTheStoreTimeZone() throws Exception {
        CreatedUser seller = createSeller();
        String sellerToken = sellerToken(seller);
        Long product = createPricedProduct("10", "0");
        addStock(product, 2);
        // Midnight in Mexico City (UTC-6) is 06:00Z: the first sale is the last microsecond of Dec 30.
        sellAt(sellerToken, product, "CASH", 1, at("2025-12-31T05:59:59.999999Z"));
        sellAt(sellerToken, product, "CASH", 1, at("2025-12-31T06:00:00Z"));
        String adminToken = adminAccessToken();
        String sellerFilter = "&sellerId=" + seller.id();

        getJson(reportUrl(LocalDate.of(2025, 12, 30), LocalDate.of(2025, 12, 30)) + sellerFilter, adminToken)
                .andExpect(jsonPath("$.completed.saleCount").value(1))
                .andExpect(jsonPath("$.byDay[0].date").value("2025-12-30"))
                .andExpect(jsonPath("$.byDay[0].saleCount").value(1));
        getJson(reportUrl(LocalDate.of(2025, 12, 31), LocalDate.of(2025, 12, 31)) + sellerFilter, adminToken)
                .andExpect(jsonPath("$.completed.saleCount").value(1))
                .andExpect(jsonPath("$.byDay[0].saleCount").value(1));
        getJson(reportUrl(LocalDate.of(2025, 12, 30), LocalDate.of(2025, 12, 31)) + sellerFilter, adminToken)
                .andExpect(jsonPath("$.completed.saleCount").value(2))
                .andExpect(jsonPath("$.byDay[0].saleCount").value(1))
                .andExpect(jsonPath("$.byDay[1].saleCount").value(1));
    }

    @Test
    void emptyPeriodReturnsZerosAndEveryDay() throws Exception {
        String body = getJson(reportUrl(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 7)), sellerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byDay.length()").value(7))
                .andExpect(jsonPath("$.byDay[6].date").value("2020-01-07"))
                .andExpect(jsonPath("$.byPaymentMethod.length()").value(0))
                .andExpect(jsonPath("$.topProducts.length()").value(0))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(
                "\"completed\":{\"saleCount\":0,\"subtotal\":0.00,\"tax\":0.00,\"total\":0.00}",
                "\"averageTicket\":0.00");
    }

    @Test
    void cashClosingCountsSalesOnTheirDayAndCancellationsOnTheDayTheyWereMade() throws Exception {
        String adminToken = adminAccessToken();
        String closingUrl = "/api/v1/sale-reports/cash-closing?date=2025-11-10";
        // The database is shared: the whole-store check compares against what was there before.
        String storeBefore = getJson(closingUrl, adminToken).andReturn().getResponse().getContentAsString();
        CreatedUser sellerA = createSeller();
        CreatedUser sellerB = createSeller();
        String tokenA = sellerToken(sellerA);
        String tokenB = sellerToken(sellerB);
        Long product = createPricedProduct("1", "0");
        addStock(product, 100);
        // Nov 10, 2025 in Mexico City (UTC-6) runs from 06:00Z to 06:00Z next day. Every cancellation
        // is made by the admin: it still belongs to the drawer of the seller who made the sale.
        sellAt(tokenA, product, "CASH", 10, at("2025-11-10T15:00:00Z"));
        sellAt(tokenA, product, "CARD", 20, at("2025-11-10T16:00:00Z"));
        // Sold and cancelled the same day: in and out of the drawer.
        cancelAt(sellAt(tokenA, product, "CASH", 5, at("2025-11-10T17:00:00Z")), at("2025-11-10T18:00:00Z"));
        // Older sale cancelled on Nov 10: the cancellation belongs to Nov 10.
        cancelAt(sellAt(tokenA, product, "TRANSFER", 7, at("2025-11-09T15:00:00Z")), at("2025-11-10T19:00:00Z"));
        // Sold on Nov 10, cancelled on Nov 11: Nov 10 keeps the sale.
        cancelAt(sellAt(tokenA, product, "CASH", 3, at("2025-11-10T20:00:00Z")), at("2025-11-11T15:00:00Z"));
        // B's movements, including a cancellation, never reach A's drawer.
        sellAt(tokenB, product, "CASH", 4, at("2025-11-10T15:30:00Z"));
        cancelAt(sellAt(tokenB, product, "CARD", 2, at("2025-11-10T16:30:00Z")), at("2025-11-10T17:30:00Z"));

        // A's drawer, whether A asks (another sellerId is ignored) or an admin filters by A.
        for (List<String> request : List.of(
                List.of(closingUrl + "&sellerId=" + sellerB.id(), tokenA),
                List.of(closingUrl + "&sellerId=" + sellerA.id(), adminToken))) {
            String body = getJson(request.get(0), request.get(1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date").value("2025-11-10"))
                    .andExpect(jsonPath("$.sellerId").value(sellerA.id()))
                    .andExpect(jsonPath("$.closed").value(true))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).contains(
                    "\"byPaymentMethod\":{"
                            + "\"CASH\":{\"saleCount\":3,\"salesTotal\":18.00,"
                            + "\"cancellationCount\":1,\"cancellationsTotal\":5.00,\"net\":13.00},"
                            + "\"CARD\":{\"saleCount\":1,\"salesTotal\":20.00,"
                            + "\"cancellationCount\":0,\"cancellationsTotal\":0.00,\"net\":20.00},"
                            + "\"TRANSFER\":{\"saleCount\":0,\"salesTotal\":0.00,"
                            + "\"cancellationCount\":1,\"cancellationsTotal\":7.00,\"net\":-7.00}}",
                    "\"total\":{\"saleCount\":4,\"salesTotal\":38.00,"
                            + "\"cancellationCount\":2,\"cancellationsTotal\":12.00,\"net\":26.00}",
                    "\"expectedCash\":13.00");
        }
        // The next day only carries the late cancellation.
        getJson("/api/v1/sale-reports/cash-closing?date=2025-11-11&sellerId=" + sellerA.id(), adminToken)
                .andExpect(jsonPath("$.total.saleCount").value(0))
                .andExpect(jsonPath("$.total.cancellationCount").value(1))
                .andExpect(jsonPath("$.expectedCash").value(-3.0));
        // The admin cancelled those sales but sold none, so filtering by the admin finds nothing.
        Long adminId = ((Number) JsonPath.read(getJson("/api/v1/users/me", adminToken)
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        getJson(closingUrl + "&sellerId=" + adminId, adminToken)
                .andExpect(jsonPath("$.total.cancellationCount").value(0));
        // The whole store adds both drawers: A's cash net 13 plus B's 4.
        String storeAfter = getJson(closingUrl, adminToken)
                .andExpect(jsonPath("$.sellerId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(number(storeAfter, "$.byPaymentMethod.CASH.saleCount")
                .subtract(number(storeBefore, "$.byPaymentMethod.CASH.saleCount"))).isEqualByComparingTo("4");
        assertThat(number(storeAfter, "$.expectedCash").subtract(number(storeBefore, "$.expectedCash")))
                .isEqualByComparingTo("17.00");
    }

    @Test
    void cashClosingCancellationsFollowTheStoreTimeZone() throws Exception {
        CreatedUser seller = createSeller();
        String token = sellerToken(seller);
        Long product = createPricedProduct("1", "0");
        addStock(product, 2);
        // Last microsecond of Nov 10 in Mexico City, and the first one of Nov 11.
        cancelAt(sellAt(token, product, "CASH", 1, at("2025-11-09T15:00:00Z")), at("2025-11-11T05:59:59.999999Z"));
        cancelAt(sellAt(token, product, "CASH", 1, at("2025-11-09T15:00:00Z")), at("2025-11-11T06:00:00Z"));
        String adminToken = adminAccessToken();

        for (String date : List.of("2025-11-10", "2025-11-11")) {
            getJson("/api/v1/sale-reports/cash-closing?date=" + date + "&sellerId=" + seller.id(), adminToken)
                    .andExpect(jsonPath("$.total.cancellationCount").value(1))
                    .andExpect(jsonPath("$.expectedCash").value(-1.0));
        }
    }

    @Test
    void cashClosingDefaultsToTodayAndRejectsInvalidDates() throws Exception {
        // Without a drawer today, so the blind count doesn't apply.
        String token = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();

        getJson("/api/v1/sale-reports/cash-closing", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("America/Mexico_City"))
                .andExpect(jsonPath("$.date").value(today().toString()))
                .andExpect(jsonPath("$.closed").value(false));
        getJson("/api/v1/sale-reports/cash-closing?date=1999-12-31", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dates must be between 2000-01-01 and 9999-12-30"));
        getJson("/api/v1/sale-reports/cash-closing?date=10/11/2025", token).andExpect(status().isBadRequest());
    }

    @Test
    void invalidPeriodsAreRejected() throws Exception {
        String token = sellerToken();

        getJson(reportUrl(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'from' must not be after 'to'"));
        // 2024 is a leap year: 366 days pass, 367 don't.
        getJson(reportUrl(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)), token)
                .andExpect(status().isOk());
        getJson(reportUrl(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The period can't exceed 366 days"));
        // ISO dates accept signed years beyond 9999; queryParam avoids URL-encoding the "+".
        mockMvc.perform(withToken(get("/api/v1/sale-reports")
                        .queryParam("from", "+999999999-12-31")
                        .queryParam("to", "+999999999-12-31"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dates must be between 2000-01-01 and 9999-12-30"));
        getJson(reportUrl(LocalDate.of(1999, 12, 31), LocalDate.of(2000, 1, 1)), token)
                .andExpect(status().isBadRequest());
        getJson("/api/v1/sale-reports?to=2026-01-01", token).andExpect(status().isBadRequest());
        getJson("/api/v1/sale-reports?from=01/01/2026&to=2026-01-31", token).andExpect(status().isBadRequest());
    }

    // sale_date is set by the server: sell through the API, then move only that column.
    private Long sellAt(String sellerToken, Long productId, String paymentMethod, int quantity, OffsetDateTime saleDate)
            throws Exception {
        Long saleId = readId(sell(oneLineBody(paymentMethod, productId, quantity), sellerToken)
                .andExpect(status().isCreated()));
        jdbcTemplate.update("UPDATE sale SET sale_date = ? WHERE id = ?", saleDate, saleId);
        return saleId;
    }

    // Cancelled by the admin through the API, then only cancelled_at is moved.
    private void cancelAt(Long saleId, OffsetDateTime cancelledAt) throws Exception {
        postJson("/api/v1/sales/" + saleId + "/cancellation", """
                {"reason": "Test cancellation"}
                """, adminAccessToken()).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE sale SET cancelled_at = ? WHERE id = ?", cancelledAt, saleId);
    }

    // PgJDBC binds OffsetDateTime as timestamptz.
    private static OffsetDateTime at(String instant) {
        return OffsetDateTime.parse(instant);
    }

    private static BigDecimal number(String json, String path) {
        return new BigDecimal(JsonPath.read(json, path).toString());
    }

    private static BigDecimal sum(String json, String path) {
        List<Object> values = JsonPath.read(json, path);
        return values.stream().map(value -> new BigDecimal(value.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate today() {
        return LocalDate.now(storeProperties.timeZone());
    }

    private static String reportUrl(LocalDate from, LocalDate to) {
        return "/api/v1/sale-reports?from=" + from + "&to=" + to;
    }

    private static String oneLineBody(String paymentMethod, Long productId, int quantity) {
        return """
                {"paymentMethod": "%s", "items": [{"productId": %d, "quantity": %d}]}
                """.formatted(paymentMethod, productId, quantity);
    }

    private static String twoLineBody(String paymentMethod, Long first, int firstQuantity, Long second,
            int secondQuantity) {
        return """
                {"paymentMethod": "%s", "items": [
                    {"productId": %d, "quantity": %d},
                    {"productId": %d, "quantity": %d}
                ]}
                """.formatted(paymentMethod, first, firstQuantity, second, secondQuantity);
    }
}
