package com.irvin.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class StockAdjustmentControllerIT extends AbstractIntegrationTest {

    private static final String ADJUSTMENTS = "/api/v1/stock-adjustments";

    @Test
    void adjustmentsAddAndRemoveStockWithAReason() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 10);

        Long found = readId(postJson(ADJUSTMENTS, adjustmentBody(product, 4, "  Found in physical count  "), token)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/inventory-movements/")))
                .andExpect(jsonPath("$.type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.resultingStock").value(14))
                .andExpect(jsonPath("$.reason").value("Found in physical count"))
                .andExpect(jsonPath("$.recordedBy.name").exists())
                .andExpect(jsonPath("$.movementDate").exists()));
        postJson(ADJUSTMENTS, adjustmentBody(product, -6, "Damaged in storage"), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultingStock").value(8));

        assertThat(stock(product)).isEqualTo(8);
        getJson("/api/v1/inventory-movements/" + found, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(product))
                .andExpect(jsonPath("$.resultingStock").value(14));
    }

    @Test
    void adjustmentCannotLeaveNegativeStock() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 2);

        postJson(ADJUSTMENTS, adjustmentBody(product, -3, "Expired"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Insufficient stock for product: " + product));
        postJson(ADJUSTMENTS, adjustmentBody(product, -2, "Expired"), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultingStock").value(0));

        assertThat(stock(product)).isZero();
    }

    @Test
    void concurrentDecrementsNeverOversellAndKeepTheLedgerExact() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 5);
        String body = adjustmentBody(product, -1, "Shrinkage");
        int attempts = 10;

        List<Integer> statuses = runConcurrently(Collections.nCopies(attempts,
                () -> httpStatus(postJson(ADJUSTMENTS, body, token))));

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(5);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(attempts - 5);
        assertThat(stock(product)).isZero();
        // resulting_stock comes from the locked row: each movement continues exactly from the previous one.
        assertThat(jdbcTemplate.queryForList("""
                SELECT resulting_stock FROM inventory_movement
                WHERE product_id = ? AND type = 'ADJUSTMENT' ORDER BY movement_date, id""",
                Integer.class, product)).containsExactly(4, 3, 2, 1, 0);
    }

    @Test
    void concurrentSalesAndAdjustmentsOnTheSameProductKeepTheLedgerConsistent() throws Exception {
        String adminToken = adminAccessToken();
        String sellerToken = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 10);
        String adjustment = adjustmentBody(product, -1, "Shrinkage");
        String sale = """
                {"paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(product);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID key = UUID.randomUUID();
            tasks.add(() -> httpStatus(postJson(ADJUSTMENTS, adjustment, adminToken)));
            tasks.add(() -> httpStatus(postJson("/api/v1/sales", sale, sellerToken, key)));
        }

        assertThat(runConcurrently(tasks)).containsOnly(201);
        assertThat(stock(product)).isZero();
        // In ledger order, every resulting stock is the previous one plus this movement's quantity.
        List<Map<String, Object>> ledger = jdbcTemplate.queryForList("""
                SELECT quantity, resulting_stock FROM inventory_movement
                WHERE product_id = ? ORDER BY movement_date, id""", product);
        assertThat(ledger).hasSize(11);
        for (int i = 1; i < ledger.size(); i++) {
            int previous = (Integer) ledger.get(i - 1).get("resulting_stock");
            int quantity = (Integer) ledger.get(i).get("quantity");
            assertThat(ledger.get(i).get("resulting_stock")).isEqualTo(previous + quantity);
        }
    }

    @Test
    void inactiveProductsCanBeWrittenOffOrRestocked() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 3);
        deactivateProduct(product);

        postJson(ADJUSTMENTS, adjustmentBody(product, -3, "Discontinued, discarded"), token)
                .andExpect(status().isCreated());
        postJson(ADJUSTMENTS, adjustmentBody(product, 2, "Returned by supplier"), token)
                .andExpect(status().isCreated());
        assertThat(stock(product)).isEqualTo(2);
    }

    @Test
    void invalidAdjustmentsAreRejected() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 5);

        postJson(ADJUSTMENTS, adjustmentBody(product, 0, "Nothing"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Quantity must not be zero"));
        postJson(ADJUSTMENTS, adjustmentBody(product, 1, "   "), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        postJson(ADJUSTMENTS, adjustmentBody(product, 1, "x".repeat(256)), token)
                .andExpect(status().isBadRequest());
        postJson(ADJUSTMENTS, adjustmentBody(product, 100_001, "Too many"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());
        postJson(ADJUSTMENTS, adjustmentBody(product, -100_001, "Too many"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());
        postJson(ADJUSTMENTS, adjustmentBody(999999L, 1, "Ghost"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product not found: 999999"));

        assertThat(stock(product)).isEqualTo(5);
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheOriginalAdjustment() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 10);
        String body = adjustmentBody(product, -5, "Damaged");
        UUID key = UUID.randomUUID();

        Long firstId = readId(postJson(ADJUSTMENTS, body, token, key).andExpect(status().isCreated()));
        postJson(ADJUSTMENTS, body, token, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.resultingStock").value(5));

        assertThat(stock(product)).isEqualTo(5);
    }

    @Test
    void reusingAKeyWithDifferentContentConflicts() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        Long other = createPricedProduct("1", "0");
        addStock(product, 10);
        UUID key = UUID.randomUUID();
        postJson(ADJUSTMENTS, adjustmentBody(product, -5, "Damaged"), token, key).andExpect(status().isCreated());

        for (String body : List.of(
                adjustmentBody(product, -4, "Damaged"),
                adjustmentBody(product, -5, "Expired"),
                adjustmentBody(other, -5, "Damaged"))) {
            postJson(ADJUSTMENTS, body, token, key)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Idempotency-Key was already used with a different request"));
        }
        assertThat(stock(product)).isEqualTo(5);
    }

    @Test
    void concurrentRetriesWithTheSameKeyRecordOneAdjustment() throws Exception {
        String token = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 10);
        String body = adjustmentBody(product, -3, "Damaged");
        UUID key = UUID.randomUUID();
        int retries = 6;

        List<Integer> statuses = runConcurrently(Collections.nCopies(retries,
                () -> httpStatus(postJson(ADJUSTMENTS, body, token, key))));

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(retries - 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_movement WHERE idempotency_key = ?", Integer.class, key)).isEqualTo(1);
        assertThat(stock(product)).isEqualTo(7);
    }

    @Test
    void sellerCannotAdjustStock() throws Exception {
        Long product = createPricedProduct("1", "0");

        postJson(ADJUSTMENTS, adjustmentBody(product, 1, "Mine"), sellerToken()).andExpect(status().isForbidden());
        assertThat(stock(product)).isZero();
    }

    private static String adjustmentBody(Long productId, int quantity, String reason) {
        return """
                {"productId": %d, "quantity": %d, "reason": "%s"}
                """.formatted(productId, quantity, reason);
    }
}
