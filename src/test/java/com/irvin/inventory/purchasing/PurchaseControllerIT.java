package com.irvin.inventory.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class PurchaseControllerIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void purchaseIncreasesStockComputesTotalsAndRecordsMovements() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long rice = createProduct(categoryId("Pantry"), 0);
        Long soda = createProduct(categoryId("Beverages"), 0);

        Long purchaseId = readId(postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": [
                    {"productId": %d, "quantity": 3, "unitCost": 12.5},
                    {"productId": %d, "quantity": 10, "unitCost": 8.25}
                ]}
                """.formatted(supplierId, soda, rice), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(120.0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.recordedBy.name").exists()));

        getJson("/api/v1/products/" + rice, token).andExpect(jsonPath("$.stock").value(10));
        getJson("/api/v1/products/" + soda, token).andExpect(jsonPath("$.stock").value(3));
        assertThat(resultingStock(purchaseId, rice)).isEqualTo(10);
        assertThat(resultingStock(purchaseId, soda)).isEqualTo(3);
        getJson("/api/v1/purchases/" + purchaseId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.productId == " + rice + ")].subtotal").value(82.5));
    }

    @Test
    void moneyIsReturnedWithTwoDecimalsOnCreateAndRead() throws Exception {
        String token = adminAccessToken();
        Long product = createProduct(categoryId("Pantry"), 0);

        // Exact JSON text: the 201 response must look like later reads (10.00, not 10).
        String created = postJson("/api/v1/purchases", purchaseBody(createSupplier(), product, 1, "10"), token)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(created).contains("\"total\":10.00").contains("\"unitCost\":10.00");
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalPurchaseWithoutAddingStockTwice() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long product = createProduct(categoryId("Dairy"), 0);
        String body = purchaseBody(supplierId, product, 5, "10.00");
        UUID key = UUID.randomUUID();

        Long firstId = readId(postJson("/api/v1/purchases", body, token, key).andExpect(status().isCreated()));
        postJson("/api/v1/purchases", body, token, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        getJson("/api/v1/products/" + product, token).andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    void reusingAKeyWithDifferentContentConflicts() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long product = createProduct(categoryId("Dairy"), 0);
        Long other = createProduct(categoryId("Dairy"), 0);
        UUID key = UUID.randomUUID();
        postJson("/api/v1/purchases", purchaseBody(supplierId, product, 5, "10.00"), token, key)
                .andExpect(status().isCreated());

        List<String> differentBodies = List.of(
                purchaseBody(supplierId, product, 6, "10.00"),
                purchaseBody(supplierId, product, 5, "11.00"),
                purchaseBody(createSupplier(), product, 5, "10.00"),
                """
                {"supplierId": %d, "items": [
                    {"productId": %d, "quantity": 5, "unitCost": 10.00},
                    {"productId": %d, "quantity": 1, "unitCost": 1.00}
                ]}
                """.formatted(supplierId, product, other));
        for (String body : differentBodies) {
            postJson("/api/v1/purchases", body, token, key).andExpect(status().isConflict());
        }
    }

    @Test
    void concurrentRetriesWithTheSameKeyRecordOnePurchase() throws Exception {
        String token = adminAccessToken();
        Long product = createProduct(categoryId("Dairy"), 0);
        String body = purchaseBody(createSupplier(), product, 4, "10.00");
        UUID key = UUID.randomUUID();
        int retries = 8;

        List<Integer> statuses = runConcurrently(Collections.nCopies(retries,
                () -> httpStatus(postJson("/api/v1/purchases", body, token, key))));

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(retries - 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM purchase WHERE idempotency_key = ?", Integer.class, key)).isEqualTo(1);
        getJson("/api/v1/products/" + product, token).andExpect(jsonPath("$.stock").value(4));
    }

    @Test
    void concurrentPurchasesOfTheSameProductKeepStockAndMovementsConsistent() throws Exception {
        String token = adminAccessToken();
        Long product = createProduct(categoryId("Snacks"), 0);
        String body = purchaseBody(createSupplier(), product, 3, "10.00");
        int purchases = 8;

        List<Integer> statuses = runConcurrently(Collections.nCopies(purchases,
                () -> httpStatus(postJson("/api/v1/purchases", body, token))));

        assertThat(statuses).containsOnly(201);
        getJson("/api/v1/products/" + product, token).andExpect(jsonPath("$.stock").value(purchases * 3));
        // Each movement continues exactly from the previous one: 3, 6, 9, ...
        assertThat(jdbcTemplate.queryForList(
                "SELECT resulting_stock FROM inventory_movement WHERE product_id = ? ORDER BY movement_date, id",
                Integer.class, product))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, purchases).map(i -> i * 3).boxed().toList());
    }

    @Test
    void crossOrderedConcurrentPurchasesDoNotDeadlock() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long a = createProduct(categoryId("Pantry"), 0);
        Long b = createProduct(categoryId("Pantry"), 0);
        String ab = twoItemBody(supplierId, a, b);
        String ba = twoItemBody(supplierId, b, a);
        int purchases = 10;

        // Without sorting items by product id, [A, B] vs [B, A] would deadlock (503).
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < purchases; i++) {
            String body = i % 2 == 0 ? ab : ba;
            tasks.add(() -> httpStatus(postJson("/api/v1/purchases", body, token)));
        }

        assertThat(runConcurrently(tasks)).containsOnly(201);
        getJson("/api/v1/products/" + a, token).andExpect(jsonPath("$.stock").value(purchases));
        getJson("/api/v1/products/" + b, token).andExpect(jsonPath("$.stock").value(purchases));
    }

    @Test
    void invalidPurchasesAreRejectedWithoutChangingStock() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long product = createProduct(categoryId("Pantry"), 0);

        postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": []}
                """.formatted(supplierId), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
        postJson("/api/v1/purchases", purchaseBody(supplierId, product, 0, "1"), token)
                .andExpect(status().isBadRequest());
        postJson("/api/v1/purchases", purchaseBody(supplierId, product, 100_001, "1"), token)
                .andExpect(status().isBadRequest());
        postJson("/api/v1/purchases", purchaseBody(supplierId, product, 1, "1.005"), token)
                .andExpect(status().isBadRequest());
        // 2.5 must not be silently truncated to 2.
        postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": [{"productId": %d, "quantity": 2.5, "unitCost": 1}]}
                """.formatted(supplierId, product), token)
                .andExpect(status().isBadRequest());
        postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": [
                    {"productId": %d, "quantity": 1, "unitCost": 1},
                    {"productId": %d, "quantity": 2, "unitCost": 1}
                ]}
                """.formatted(supplierId, product, product), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Each product can appear only once per purchase"));
        postJson("/api/v1/purchases", purchaseBody(supplierId, 999999L, 1, "1"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product not found: 999999"));
        postJson("/api/v1/purchases", purchaseBody(999999L, product, 1, "1"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Supplier not found"));
        mockMvc.perform(withToken(post("/api/v1/purchases"), token)
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, product, 1, "1")))
                .andExpect(status().isBadRequest());

        getJson("/api/v1/products/" + product, token).andExpect(jsonPath("$.stock").value(0));
    }

    @Test
    void totalAboveTheMaximumIsRejected() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long a = createProduct(categoryId("Pantry"), 0);
        Long b = createProduct(categoryId("Pantry"), 0);

        // Each line is within limits (9,999,999,000) but the total exceeds NUMERIC(12,2).
        postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": [
                    {"productId": %d, "quantity": 100000, "unitCost": 99999.99},
                    {"productId": %d, "quantity": 100000, "unitCost": 99999.99}
                ]}
                """.formatted(supplierId, a, b), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Purchase total exceeds the maximum allowed"));
    }

    @Test
    void inactiveSupplierOrProductIsRejected() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long product = createProduct(categoryId("Pantry"), 0);
        String sku = jdbcTemplate.queryForObject("SELECT sku FROM product WHERE id = ?", String.class, product);
        putJson("/api/v1/products/" + product, """
                {"sku": "%s", "name": "Old", "categoryId": %d, "salePrice": 1, "taxRate": 0,
                 "minStock": 0, "active": false, "version": 0}
                """.formatted(sku, categoryId("Pantry")), token).andExpect(status().isOk());

        postJson("/api/v1/purchases", purchaseBody(supplierId, product, 1, "1"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product is inactive: " + product));

        putJson("/api/v1/suppliers/" + supplierId, """
                {"name": "Closed", "active": false, "version": 0}
                """, token).andExpect(status().isOk());
        postJson("/api/v1/purchases", purchaseBody(supplierId, createProduct(categoryId("Pantry"), 0), 1, "1"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Supplier is inactive"));
    }

    @Test
    void listFiltersBySupplierAndDateRange() throws Exception {
        String token = adminAccessToken();
        Long supplierId = createSupplier();
        Long product = createProduct(categoryId("Pantry"), 0);
        postJson("/api/v1/purchases", purchaseBody(supplierId, product, 1, "1"), token).andExpect(status().isCreated());
        postJson("/api/v1/purchases", purchaseBody(supplierId, product, 2, "1"), token).andExpect(status().isCreated());

        getJson("/api/v1/purchases?supplierId=" + supplierId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].items").doesNotExist());
        getJson("/api/v1/purchases?supplierId=" + supplierId + "&from=2100-01-01T00:00:00Z", token)
                .andExpect(jsonPath("$.content.length()").value(0));
        getJson("/api/v1/purchases/999999", token).andExpect(status().isNotFound());
    }

    @Test
    void sellerCannotRecordOrViewPurchases() throws Exception {
        String sellerToken = loginOk(createSeller().email(), DEFAULT_PASSWORD).accessToken();
        Long product = createProduct(categoryId("Pantry"), 0);

        postJson("/api/v1/purchases", purchaseBody(createSupplier(), product, 1, "1"), sellerToken)
                .andExpect(status().isForbidden());
        getJson("/api/v1/purchases", sellerToken).andExpect(status().isForbidden());
    }

    private Integer resultingStock(Long purchaseId, Long productId) {
        return jdbcTemplate.queryForObject("""
                SELECT resulting_stock FROM inventory_movement
                WHERE purchase_id = ? AND product_id = ? AND type = 'PURCHASE'""",
                Integer.class, purchaseId, productId);
    }

    private static String purchaseBody(Long supplierId, Long productId, int quantity, String unitCost) {
        return """
                {"supplierId": %d, "items": [{"productId": %d, "quantity": %d, "unitCost": %s}]}
                """.formatted(supplierId, productId, quantity, unitCost);
    }

    private static String twoItemBody(Long supplierId, Long first, Long second) {
        return """
                {"supplierId": %d, "items": [
                    {"productId": %d, "quantity": 1, "unitCost": 1},
                    {"productId": %d, "quantity": 1, "unitCost": 1}
                ]}
                """.formatted(supplierId, first, second);
    }
}
