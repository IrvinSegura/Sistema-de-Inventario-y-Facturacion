package com.irvin.inventory.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.irvin.inventory.AbstractIntegrationTest;
import com.irvin.inventory.config.StoreProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CashDrawerControllerIT extends AbstractIntegrationTest {

    @Autowired
    private StoreProperties storeProperties;

    @Test
    void openSellAndCloseRecordsTheExpectedCashAndTheDifference() throws Exception {
        CreatedUser seller = createSeller();
        String token = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("10.00", "0");
        addStock(product, 10);

        sell(saleBody(product, 1, "CASH"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Open your cash drawer before selling"));
        getJson(CASH_DRAWERS + "/current", token).andExpect(status().isNotFound());

        Long drawerId = readId(postJson(CASH_DRAWERS, openBody("500"), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsible.id").value(seller.id()))
                .andExpect(jsonPath("$.openedBy.id").value(seller.id()))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].id").value(seller.id()))
                .andExpect(jsonPath("$.businessDate").value(today().toString()))
                .andExpect(jsonPath("$.openingFloat").value(500.0))
                .andExpect(jsonPath("$.closing").doesNotExist()));
        postJson(CASH_DRAWERS, openBody("100"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Your cash drawer is already open"));
        getJson(CASH_DRAWERS + "/current", token).andExpect(jsonPath("$.id").value(drawerId));

        UUID key = UUID.randomUUID();
        Long firstSale = readId(postJson("/api/v1/sales", saleBody(product, 3, "CASH"), token, key)
                .andExpect(status().isCreated()));
        // Card payments never go through the drawer.
        sell(saleBody(product, 2, "CARD"), token).andExpect(status().isCreated());
        Long cancelledSaleId = readId(sell(saleBody(product, 1, "CASH"), token).andExpect(status().isCreated()));
        cancel(cancelledSaleId, adminToken);

        // Blind count: the seller can't see the expected cash before declaring the counted cash.
        getJson("/api/v1/sale-reports/cash-closing", token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Close your cash drawer before viewing its cash closing"));
        getJson("/api/v1/sale-reports/cash-closing?sellerId=" + seller.id(), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCash").value(530.0));

        // 500 float + 30 cash; the cancelled sale nets 0.
        postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closeBody("527.50", "  Short on coins  "), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closing.closedBy.id").value(seller.id()))
                .andExpect(jsonPath("$.closing.expectedCash").value(530.0))
                .andExpect(jsonPath("$.closing.countedCash").value(527.5))
                .andExpect(jsonPath("$.closing.difference").value(-2.5))
                .andExpect(jsonPath("$.closing.note").value("Short on coins"));

        sell(saleBody(product, 1, "CASH"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Your cash drawer for today is closed"));
        // A retried checkout still gets its recorded sale.
        postJson("/api/v1/sales", saleBody(product, 3, "CASH"), token, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstSale));
        postJson(CASH_DRAWERS, openBody("0"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Today's cash drawer is already closed"));
        postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closeBody("530", null), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cash drawer is already closed"));
        getJson(CASH_DRAWERS + "/current", token).andExpect(status().isNotFound());

        // The cash closing agrees with the drawer's snapshot while nothing else changes.
        getJson("/api/v1/sale-reports/cash-closing", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingFloat").value(500.0))
                .andExpect(jsonPath("$.expectedCash").value(530.0))
                .andExpect(jsonPath("$.drawers.length()").value(1))
                .andExpect(jsonPath("$.drawers[0].id").value(drawerId))
                .andExpect(jsonPath("$.drawers[0].closing.difference").value(-2.5));
    }

    @Test
    void aDrawerCountsTheSalesRecordedInItAndTheRefundsThatCameOutOfIt() throws Exception {
        CreatedUser seller = createSeller();
        String token = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
        String adminToken = adminAccessToken();
        Long yesterdays = insertDrawer(seller.id(), today().minusDays(1), "0", false);
        Long drawerId = openDrawer(token, "100");
        Long product = createPricedProduct("10.00", "0");
        addStock(product, 10);
        Long saleId = readId(sell(saleBody(product, 3, "CASH"), token).andExpect(status().isCreated()));
        assertThat(jdbcTemplate.queryForObject("SELECT cash_drawer_id FROM sale WHERE id = ?", Long.class, saleId))
                .isEqualTo(drawerId);
        sell(saleBody(product, 2, "CARD"), token).andExpect(status().isCreated());
        // Recorded in yesterday's drawer, cancelled today: the refund comes out of today's.
        Long older = readId(sell(saleBody(product, 1, "CASH"), token).andExpect(status().isCreated()));
        jdbcTemplate.update("UPDATE sale SET cash_drawer_id = ?, sale_date = sale_date - interval '1 day' WHERE id = ?",
                yesterdays, older);
        cancel(older, adminToken);
        assertThat(jdbcTemplate.queryForObject("SELECT cancellation_drawer_id FROM sale WHERE id = ?", Long.class,
                older)).isEqualTo(drawerId);
        // Moving its cancellation to another day doesn't take the refund out of this drawer.
        Long later = readId(sell(saleBody(product, 2, "CASH"), token).andExpect(status().isCreated()));
        cancel(later, adminToken);
        jdbcTemplate.update("UPDATE sale SET cancelled_at = cancelled_at + interval '1 day' WHERE id = ?", later);

        // 100 + 30 + 20 - 10 - 20.
        String drawerClosing = "/api/v1/sale-reports/cash-closing?cashDrawerId=" + drawerId;
        getJson(drawerClosing, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashDrawerId").value(drawerId))
                .andExpect(jsonPath("$.date").value(today().toString()))
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.byPaymentMethod.CASH.saleCount").value(2))
                .andExpect(jsonPath("$.byPaymentMethod.CASH.cancellationCount").value(2))
                .andExpect(jsonPath("$.byPaymentMethod.CARD.net").value(20.0))
                .andExpect(jsonPath("$.bySeller.length()").value(1))
                .andExpect(jsonPath("$.bySeller[0].seller.id").value(seller.id()))
                .andExpect(jsonPath("$.bySeller[0].cash.net").value(20.0))
                .andExpect(jsonPath("$.bySeller[0].total.net").value(40.0))
                .andExpect(jsonPath("$.expectedCash").value(120.0));
        // Blind count, also for one drawer.
        getJson(drawerClosing, token).andExpect(status().isConflict());
        postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closeBody("120", null), token)
                .andExpect(jsonPath("$.closing.expectedCash").value(120.0));
        getJson(drawerClosing, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.expectedCash").value(120.0));
        // Another seller can't read it.
        getJson(drawerClosing, sellerToken()).andExpect(status().isNotFound());
    }

    @Test
    void cancellationsAfterTheClosingKeepTheSnapshotButShowInTheCashClosing() throws Exception {
        CreatedUser seller = createSeller();
        String token = sellerToken(seller);
        Long drawerId = readId(getJson(CASH_DRAWERS + "/current", token));
        Long product = createPricedProduct("10.00", "0");
        addStock(product, 1);
        Long saleId = readId(sell(saleBody(product, 1, "CASH"), token).andExpect(status().isCreated()));
        postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closeBody("10", null), token).andExpect(status().isOk());

        // Allowed: the refund comes from wherever the admin keeps cash, not from the closed drawer.
        cancel(saleId, adminAccessToken());

        getJson(CASH_DRAWERS + "/" + drawerId, token)
                .andExpect(jsonPath("$.closing.expectedCash").value(10.0))
                .andExpect(jsonPath("$.closing.difference").value(0.0));
        getJson("/api/v1/sale-reports/cash-closing", token)
                .andExpect(jsonPath("$.expectedCash").value(0.0))
                .andExpect(jsonPath("$.drawers[0].closing.expectedCash").value(10.0));
    }

    @Test
    void sellersOnlySeeAndCloseTheirOwnDrawers() throws Exception {
        CreatedUser seller = createSeller();
        String sellerToken = sellerToken(seller);
        String otherToken = sellerToken();
        String adminToken = adminAccessToken();
        Long drawerId = readId(getJson(CASH_DRAWERS + "/current", sellerToken).andExpect(status().isOk()));

        getJson(CASH_DRAWERS + "/" + drawerId, otherToken).andExpect(status().isNotFound());
        postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closeBody("0", null), otherToken)
                .andExpect(status().isNotFound());
        // The userId filter can't widen a seller's view.
        String body = getJson(CASH_DRAWERS + "?sellerId=" + seller.id(), otherToken)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(body, "$.content[*].responsible.id")).doesNotContain(seller.id().intValue());

        getJson(CASH_DRAWERS + "?sellerId=" + seller.id(), adminToken)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(drawerId));
        getJson(CASH_DRAWERS + "/" + drawerId, adminToken).andExpect(status().isOk());
        getJson(CASH_DRAWERS + "/999999999", adminToken).andExpect(status().isNotFound());

        // An admin closes a drawer the seller left open.
        Long adminId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, ADMIN_EMAIL);
        postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closeBody("0", null), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsible.id").value(seller.id()))
                .andExpect(jsonPath("$.closing.closedBy.id").value(adminId))
                .andExpect(jsonPath("$.closing.difference").value(0.0));
    }

    @Test
    void aDrawerLeftOpenOnAPreviousDayMustBeClosedFirst() throws Exception {
        CreatedUser seller = createSeller();
        String token = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
        Long product = createPricedProduct("10.00", "0");
        addStock(product, 1);
        LocalDate yesterday = today().minusDays(1);
        Long oldDrawer = insertDrawer(seller.id(), yesterday, "200", true);

        postJson(CASH_DRAWERS, openBody("0"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Close your cash drawer of " + yesterday + " first"));
        sell(saleBody(product, 1, "CASH"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Open your cash drawer before selling"));

        postJson(CASH_DRAWERS + "/" + oldDrawer + "/closing", closeBody("200", null), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(yesterday.toString()))
                .andExpect(jsonPath("$.closing.expectedCash").value(200.0));
        openDrawer(token, "0");
        sell(saleBody(product, 1, "CASH"), token).andExpect(status().isCreated());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        String token = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();

        for (String body : List.of("{}", openBody("-1"), openBody("1000000"), openBody("1.234"))) {
            postJson(CASH_DRAWERS, body, token).andExpect(status().isBadRequest());
        }
        Long drawerId = openDrawer(token, "0");
        String url = CASH_DRAWERS + "/" + drawerId + "/closing";
        for (String body : List.of("{}", closeBody("-1", null), closeBody("1.234", null),
                closeBody("0", "x".repeat(256)))) {
            postJson(url, body, token).andExpect(status().isBadRequest());
        }
        getJson(CASH_DRAWERS + "/current", token).andExpect(jsonPath("$.closing").doesNotExist());

        getJson(CASH_DRAWERS + "?from=2026-02-01&to=2026-01-01", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'from' must not be after 'to'"));
        getJson(CASH_DRAWERS + "?from=1999-12-31", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dates must be between 2000-01-01 and 9999-12-30"));
    }

    @Test
    void salesRacingTheClosingAreEitherCountedOrRejected() throws Exception {
        CreatedUser seller = createSeller();
        String token = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
        Long drawerId = openDrawer(token, "100");
        Long product = createPricedProduct("10.00", "0");
        addStock(product, 20);
        String sale = saleBody(product, 1, "CASH");
        String closing = closeBody("100", null);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> httpStatus(sell(sale, token)));
        }
        tasks.add(() -> httpStatus(postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closing, token)));
        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses.getLast()).isEqualTo(200);
        long sold = statuses.subList(0, 10).stream().filter(s -> s == 201).count();
        assertThat(statuses.subList(0, 10)).allMatch(s -> s == 201 || s == 409);
        String body = getJson(CASH_DRAWERS + "/" + drawerId, token).andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(JsonPath.read(body, "$.closing.expectedCash").toString()))
                .isEqualByComparingTo(BigDecimal.valueOf(100 + 10 * sold));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sale WHERE user_id = ?", Long.class, seller.id()))
                .isEqualTo(sold);
    }

    @Test
    void cancellationsRacingTheClosingAreCountedIfStampedBeforeIt() throws Exception {
        CreatedUser seller = createSeller();
        String token = sellerToken(seller);
        String adminToken = adminAccessToken();
        Long drawerId = readId(getJson(CASH_DRAWERS + "/current", token));
        Long product = createPricedProduct("10.00", "0");
        addStock(product, 5);
        List<Long> sales = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sales.add(readId(sell(saleBody(product, 1, "CASH"), token).andExpect(status().isCreated())));
        }
        String cancellation = """
                {"reason": "Race"}
                """;
        String closing = closeBody("0", null);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (Long saleId : sales) {
            tasks.add(() -> httpStatus(postJson("/api/v1/sales/" + saleId + "/cancellation", cancellation, adminToken)));
        }
        tasks.add(() -> httpStatus(postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closing, token)));
        assertThat(runConcurrently(tasks)).containsOnly(200);

        // Every cancellation is stamped either before the closing (and counted) or after it.
        long countedCancellations = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM sale s JOIN cash_drawer d ON d.id = ?
                WHERE s.user_id = ? AND s.cancelled_at < d.closed_at""", Long.class, drawerId, seller.id());
        BigDecimal expected = jdbcTemplate.queryForObject(
                "SELECT expected_cash FROM cash_drawer WHERE id = ?", BigDecimal.class, drawerId);
        assertThat(expected).isEqualByComparingTo(BigDecimal.valueOf(50 - 10 * countedCancellations));
    }

    @Test
    void concurrentOpensCreateOneDrawer() throws Exception {
        String token = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();
        String body = openBody("0");

        List<Integer> statuses = runConcurrently(Collections.nCopies(5,
                () -> httpStatus(postJson(CASH_DRAWERS, body, token))));

        assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 409);
    }

    private void cancel(Long saleId, String adminToken) throws Exception {
        postJson("/api/v1/sales/" + saleId + "/cancellation", """
                {"reason": "Returned"}
                """, adminToken).andExpect(status().isOk());
    }

    private LocalDate today() {
        return LocalDate.now(storeProperties.timeZone());
    }

    private static String openBody(String openingFloat) {
        return """
                {"openingFloat": %s}
                """.formatted(openingFloat);
    }

    private static String closeBody(String countedCash, String note) {
        return """
                {"countedCash": %s, "note": %s}
                """.formatted(countedCash, note == null ? "null" : "\"" + note + "\"");
    }

    private static String saleBody(Long productId, int quantity, String paymentMethod) {
        return """
                {"paymentMethod": "%s", "items": [{"productId": %d, "quantity": %d}]}
                """.formatted(paymentMethod, productId, quantity);
    }
}
