package com.irvin.inventory.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import com.irvin.inventory.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

class PurchaseCancellationIT extends AbstractIntegrationTest {

    @Test
    void cancellingTakesTheUnitsBackOutOfStock() throws Exception {
        String token = adminAccessToken();
        Long product = createProduct(categoryId("Pantry"), 0);
        addStock(product, 2);
        Long purchaseId = purchase(product, 5, "10.00");

        postJson(cancellationUrl(purchaseId), cancelBody("  Wrong supplier  "), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellation.reason").value("Wrong supplier"))
                .andExpect(jsonPath("$.cancellation.cancelledAt").exists())
                .andExpect(jsonPath("$.cancellation.cancelledBy.name").exists())
                .andExpect(jsonPath("$.items.length()").value(1));

        assertThat(stock(product)).isEqualTo(2);
        assertThat(cancellationMovement(purchaseId, product)).containsExactly(-5, 2);
        postJson(cancellationUrl(purchaseId), cancelBody("Again"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Purchase is already cancelled"));
        getJson("/api/v1/purchases/" + purchaseId, token)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellation.reason").value("Wrong supplier"));
    }

    @Test
    void listFiltersByStatusAndCarriesTheCancellation() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long product = createProduct(categoryId("Pantry"), 0);
        Long kept = purchase(supplierId, product, 1, "1.00");
        Long cancelled = purchase(supplierId, product, 1, "1.00");
        postJson(cancellationUrl(cancelled), cancelBody("Duplicated"), token).andExpect(status().isOk());

        String url = "/api/v1/purchases?supplierId=" + supplierId;
        getJson(url + "&status=CANCELLED", token)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(cancelled))
                .andExpect(jsonPath("$.content[0].cancellation.reason").value("Duplicated"));
        getJson(url + "&status=COMPLETED", token)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(kept))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].cancellation").doesNotExist());
        getJson(url + "&sort=status,desc", token).andExpect(jsonPath("$.content[0].id").value(kept));
    }

    @Test
    void listFiltersByCancellationDate() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long product = createProduct(categoryId("Pantry"), 0);
        purchase(supplierId, product, 1, "1.00");
        Long cancelled = purchase(supplierId, product, 1, "1.00");
        String cancelledAt = JsonPath.read(postJson(cancellationUrl(cancelled), cancelBody("Duplicated"), token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.cancellation.cancelledAt");
        String url = "/api/v1/purchases?supplierId=" + supplierId;

        // [cancelledFrom, cancelledTo) on the cancellation date; purchases never cancelled don't match.
        getJson(url + "&cancelledFrom=" + cancelledAt, token)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(cancelled));
        getJson(url + "&cancelledTo=" + cancelledAt, token)
                .andExpect(jsonPath("$.content.length()").value(0));
        Instant after = Instant.parse(cancelledAt).plus(1, ChronoUnit.HOURS);
        getJson(url + "&cancelledFrom=" + after + "&cancelledTo=" + cancelledAt, token)
                .andExpect(status().isBadRequest());
    }

    @Test
    void unitsAlreadySoldBlockTheWholeCancellation() throws Exception {
        String token = adminAccessToken();
        Long kept = createPricedProduct("10", "0");
        Long sold = createPricedProduct("10", "0");
        Long purchaseId = readId(postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": [
                    {"productId": %d, "quantity": 4, "unitCost": 5},
                    {"productId": %d, "quantity": 4, "unitCost": 5}
                ]}
                """.formatted(createSupplier(), kept, sold), token).andExpect(status().isCreated()));
        sell("""
                {"paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(sold), sellerToken()).andExpect(status().isCreated());

        postJson(cancellationUrl(purchaseId), cancelBody("Wrong count"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Insufficient stock for product: " + sold));

        // Rolled back: the product with enough units keeps them and the purchase stays completed.
        assertThat(stock(kept)).isEqualTo(4);
        assertThat(stock(sold)).isEqualTo(3);
        assertThat(cancellationMovement(purchaseId, kept)).isEmpty();
        getJson("/api/v1/purchases/" + purchaseId, token).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void concurrentCancellationsTakeTheUnitsOnce() throws Exception {
        String token = adminAccessToken();
        Long product = createProduct(categoryId("Snacks"), 0);
        addStock(product, 3);
        Long purchaseId = purchase(product, 4, "2.00");
        String body = cancelBody("Duplicated");
        int attempts = 6;

        List<Integer> statuses = runConcurrently(Collections.nCopies(attempts,
                () -> httpStatus(postJson(cancellationUrl(purchaseId), body, token))));

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(attempts - 1);
        assertThat(stock(product)).isEqualTo(3);
    }

    @Test
    void cancellingTakesTheUnitsEvenIfTheProductWasDeactivated() throws Exception {
        Long product = createProduct(categoryId("Pantry"), 0);
        Long purchaseId = purchase(product, 2, "1.00");
        deactivateProduct(product);

        postJson(cancellationUrl(purchaseId), cancelBody("Discontinued"), adminAccessToken())
                .andExpect(status().isOk());
        assertThat(stock(product)).isZero();
    }

    @Test
    void cancellingTakesThePurchaseCostOutOfTheAverage() throws Exception {
        Long product = createProduct(categoryId("Pantry"), 0);
        purchase(product, 10, "4.00");
        Long expensive = purchase(product, 30, "6.00");
        assertThat(averageCost(product)).isEqualByComparingTo("5.5");

        // (40 x 5.50 - 30 x 6.00) / 10 = 4.00.
        postJson(cancellationUrl(expensive), cancelBody("Wrong cost"), adminAccessToken()).andExpect(status().isOk());

        assertThat(averageCost(product)).isEqualByComparingTo("4");
    }

    @Test
    void laterPurchasesStillLetTheCostBeTakenOut() throws Exception {
        Long product = createProduct(categoryId("Pantry"), 0);
        Long cheap = purchase(product, 10, "4.00");
        purchase(product, 10, "6.00");
        purchase(product, 20, "5.00");

        // Without the cheap one: (10 x 6.00 + 20 x 5.00) / 30 = 5.3333.
        postJson(cancellationUrl(cheap), cancelBody("Wrong cost"), adminAccessToken()).andExpect(status().isOk());

        assertThat(averageCost(product)).isEqualByComparingTo("5.3333");
    }

    @Test
    void averageCostStaysWhenUnitsLeftSinceThePurchase() throws Exception {
        Long product = createPricedProduct("10", "0");
        purchase(product, 10, "4.00");
        Long expensive = purchase(product, 10, "6.00");
        sell("""
                {"paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 7}]}
                """.formatted(product), sellerToken()).andExpect(status().isCreated());

        // The 7 units sold left at 5.00, part of it this purchase's cost: (13 x 5 - 10 x 6) / 3 would be 1.67.
        postJson(cancellationUrl(expensive), cancelBody("Wrong cost"), adminAccessToken()).andExpect(status().isOk());

        assertThat(stock(product)).isEqualTo(3);
        assertThat(averageCost(product)).isEqualByComparingTo("5");
    }

    @Test
    void salesBeforeThePurchaseDoNotKeepTheCost() throws Exception {
        Long product = createPricedProduct("10", "0");
        purchase(product, 10, "4.00");
        sell("""
                {"paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 5}]}
                """.formatted(product), sellerToken()).andExpect(status().isCreated());
        // (5 x 4.00 + 5 x 6.00) / 10 = 5.00.
        Long later = purchase(product, 5, "6.00");

        postJson(cancellationUrl(later), cancelBody("Wrong cost"), adminAccessToken()).andExpect(status().isOk());

        assertThat(averageCost(product)).isEqualByComparingTo("4");
    }

    @Test
    void earlierPurchaseCancellationsDoNotKeepTheCost() throws Exception {
        Long product = createProduct(categoryId("Pantry"), 0);
        Long first = purchase(product, 10, "10.00");
        Long second = purchase(product, 10, "20.00");
        purchase(product, 10, "30.00");
        String token = adminAccessToken();

        postJson(cancellationUrl(first), cancelBody("Wrong cost"), token).andExpect(status().isOk());
        assertThat(averageCost(product)).isEqualByComparingTo("25");
        // That cancellation left at its own cost: only the 30.00 units remain.
        postJson(cancellationUrl(second), cancelBody("Wrong cost"), token).andExpect(status().isOk());

        assertThat(averageCost(product)).isEqualByComparingTo("30");
    }

    @Test
    void unknownAverageCostStaysUnknown() throws Exception {
        Long product = createProduct(categoryId("Pantry"), 0);
        purchase(product, 5, "3.00");
        Long purchaseId = purchase(product, 2, "3.00");
        // Like a product bought before V16 that the backfill couldn't cost.
        jdbcTemplate.update("UPDATE product SET average_cost = NULL WHERE id = ?", product);

        postJson(cancellationUrl(purchaseId), cancelBody("Duplicated"), adminAccessToken()).andExpect(status().isOk());

        assertThat(averageCost(product)).isNull();
    }

    @Test
    void supervisorsCancelAndTheLedgerListsIt() throws Exception {
        String supervisorToken = loginOk(createUser("SUPERVISOR").email(), DEFAULT_PASSWORD).accessToken();
        Long product = createProduct(categoryId("Pantry"), 0);
        Long purchaseId = purchase(product, 3, "2.00");

        postJson(cancellationUrl(purchaseId), cancelBody("Wrong supplier"), supervisorToken).andExpect(status().isOk());

        Instant now = Instant.now();
        getJson("/api/v1/inventory-movements?type=PURCHASE_CANCEL&from=" + now.minus(1, ChronoUnit.HOURS)
                + "&to=" + now.plus(1, ChronoUnit.HOURS) + "&size=100", supervisorToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.purchaseId == " + purchaseId + ")].quantity").value(-3));
    }

    @Test
    void averageCostStaysWhenNoUnitsAreLeft() throws Exception {
        Long product = createProduct(categoryId("Pantry"), 0);
        Long only = purchase(product, 3, "7.00");

        postJson(cancellationUrl(only), cancelBody("Never arrived"), adminAccessToken()).andExpect(status().isOk());

        assertThat(stock(product)).isZero();
        // The next purchase finds no units and sets the cost on its own.
        assertThat(averageCost(product)).isEqualByComparingTo("7");
    }

    @Test
    void invalidCancellationsAreRejected() throws Exception {
        String token = adminAccessToken();
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();
        Long product = createProduct(categoryId("Pantry"), 0);
        Long purchaseId = purchase(product, 1, "1.00");
        String url = cancellationUrl(purchaseId);

        postJson(url, cancelBody("Mine"), sellerToken).andExpect(status().isForbidden());
        postJson(url, cancelBody("   "), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        postJson(url, cancelBody("x".repeat(256)), token).andExpect(status().isBadRequest());
        postJson(cancellationUrl(999999L), cancelBody("Nope"), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Purchase not found"));

        assertThat(stock(product)).isEqualTo(1);
    }

    private Long purchase(Long productId, int quantity, String unitCost) throws Exception {
        return purchase(createSupplier(), productId, quantity, unitCost);
    }

    private Long purchase(Long supplierId, Long productId, int quantity, String unitCost) throws Exception {
        return readId(postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": [{"productId": %d, "quantity": %d, "unitCost": %s}]}
                """.formatted(supplierId, productId, quantity, unitCost), adminAccessToken())
                .andExpect(status().isCreated()));
    }

    // [quantity, resulting stock], or empty if there is none.
    private List<Integer> cancellationMovement(Long purchaseId, Long productId) {
        return jdbcTemplate.query("""
                SELECT quantity, resulting_stock FROM inventory_movement
                WHERE purchase_id = ? AND product_id = ? AND type = 'PURCHASE_CANCEL'""",
                rs -> rs.next() ? List.of(rs.getInt("quantity"), rs.getInt("resulting_stock")) : List.of(),
                purchaseId, productId);
    }

    private BigDecimal averageCost(Long productId) {
        return jdbcTemplate.queryForObject("SELECT average_cost FROM product WHERE id = ?", BigDecimal.class, productId);
    }

    private static String cancellationUrl(Long purchaseId) {
        return "/api/v1/purchases/" + purchaseId + "/cancellation";
    }

    private static String cancelBody(String reason) {
        return """
                {"reason": "%s"}
                """.formatted(reason);
    }
}
