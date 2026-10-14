package com.irvin.inventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        String sellerToken = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
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
        String sellerToken = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
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
        String tokenA = loginOk(sellerA.email(), DEFAULT_PASSWORD).accessToken();
        String tokenB = loginOk(sellerB.email(), DEFAULT_PASSWORD).accessToken();
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
        Long product = createPricedProduct("10", "0");
        // Midnight in Mexico City (UTC-6) is 06:00Z: the first sale is the last microsecond of Dec 30.
        insertPastSale(seller.id(), product, "2025-12-31T05:59:59.999999Z");
        insertPastSale(seller.id(), product, "2025-12-31T06:00:00Z");
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

    // sale_date is set by the server, so a past date needs a direct insert. One 10.00 line, like a real sale.
    private void insertPastSale(Long sellerId, Long productId, String saleDate) {
        Long saleId = jdbcTemplate.queryForObject("""
                INSERT INTO sale (customer_id, user_id, sale_date, subtotal, tax, total, payment_method, idempotency_key)
                SELECT id, ?, CAST(? AS timestamptz), 10.00, 0.00, 10.00, 'CASH', gen_random_uuid()
                FROM customer WHERE tax_id = 'XAXX010101000'
                RETURNING id""", Long.class, sellerId, saleDate);
        jdbcTemplate.update("""
                INSERT INTO sale_item (sale_id, product_id, quantity, unit_price, tax_rate, subtotal, tax)
                VALUES (?, ?, 1, 10.00, 0, 10.00, 0.00)""", saleId, productId);
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
