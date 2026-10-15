package com.irvin.inventory.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.irvin.inventory.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class SaleControllerIT extends AbstractIntegrationTest {

    @Test
    void saleSnapshotsPricesComputesTaxPerLineAndDecrementsStock() throws Exception {
        String sellerToken = sellerToken();
        Long soda = createPricedProduct("10.03", "0.16");
        Long rice = createPricedProduct("25.5", "0");
        addStock(soda, 5);
        addStock(rice, 5);

        // Exact JSON text: money always has two decimals, also on the 201 response.
        String created = sell("""
                {"paymentMethod": "CARD", "items": [
                    {"productId": %d, "quantity": 2},
                    {"productId": %d, "quantity": 3}
                ]}
                """.formatted(rice, soda), sellerToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saleNumber").isNumber())
                .andExpect(jsonPath("$.customer.name").value("PUBLICO EN GENERAL"))
                .andExpect(jsonPath("$.seller.name").exists())
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.cancellation").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        // 3 x 10.03 = 30.09, tax 4.8144 -> 4.81; 2 x 25.50 = 51.00, tax 0.
        assertThat(created)
                .contains("\"subtotal\":81.09", "\"tax\":4.81", "\"total\":85.90", "\"unitPrice\":25.50");

        Long saleId = ((Number) JsonPath.read(created, "$.id")).longValue();
        assertThat(stock(soda)).isEqualTo(2);
        assertThat(stock(rice)).isEqualTo(3);
        assertThat(movement(saleId, soda, "SALE")).containsExactly(-3, 2);
        assertThat(movement(saleId, rice, "SALE")).containsExactly(-2, 3);
    }

    @Test
    void taxIsRoundedPerLineNotOnTheTotal() throws Exception {
        String token = sellerToken();
        Long a = createPricedProduct("0.10", "0.05");
        Long b = createPricedProduct("0.10", "0.05");
        addStock(a, 1);
        addStock(b, 1);

        // Each line: 0.005 -> 0.01, so the sale tax is 0.02 (rounding 0.20 * 0.05 once would give 0.01).
        sell(twoItemBody(a, b), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tax").value(0.02))
                .andExpect(jsonPath("$.total").value(0.22));
    }

    @Test
    void pastSalesKeepTheirPriceAfterTheProductChanges() throws Exception {
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("10.00", "0.16");
        addStock(product, 1);
        Long saleId = readId(sell(saleBody(product, 1), adminToken).andExpect(status().isCreated()));
        String sku = jdbcTemplate.queryForObject("SELECT sku FROM product WHERE id = ?", String.class, product);

        putJson("/api/v1/products/" + product, """
                {"sku": "%s", "name": "Repriced", "categoryId": %d, "salePrice": 99, "taxRate": 0,
                 "minStock": 0, "active": true, "version": 0}
                """.formatted(sku, categoryId("Pantry")), adminToken).andExpect(status().isOk());

        getJson("/api/v1/sales/" + saleId, adminToken)
                .andExpect(jsonPath("$.items[0].unitPrice").value(10.0))
                .andExpect(jsonPath("$.items[0].taxRate").value(0.16))
                .andExpect(jsonPath("$.total").value(11.6));
    }

    @Test
    void saleNumbersComeFromASequence() throws Exception {
        String token = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 2);

        long first = readSaleNumber(sell(saleBody(product, 1), token).andExpect(status().isCreated()));
        long second = readSaleNumber(sell(saleBody(product, 1), token).andExpect(status().isCreated()));

        assertThat(second).isGreaterThan(first);
    }

    @Test
    void saleToARegisteredCustomer() throws Exception {
        String token = sellerToken();
        Long customer = createCustomer();
        Long product = createPricedProduct("1", "0");
        addStock(product, 1);

        sell("""
                {"customerId": %d, "paymentMethod": "TRANSFER", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(customer, product), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.id").value(customer));
    }

    @Test
    void tenSellersRacingForTheLastUnitProduceExactlyOneSale() throws Exception {
        Long product = createPricedProduct("15.00", "0");
        addStock(product, 1);
        int sellers = 10;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < sellers; i++) {
            tokens.add(sellerToken());
        }
        String body = saleBody(product, 1);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (String token : tokens) {
            UUID key = UUID.randomUUID();
            tasks.add(() -> httpStatus(postJson("/api/v1/sales", body, token, key)));
        }
        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(sellers - 1);
        assertThat(stock(product)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sale_item WHERE product_id = ?", Integer.class, product)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT resulting_stock FROM inventory_movement WHERE product_id = ? AND type = 'SALE'",
                Integer.class, product)).containsExactly(0);
    }

    @Test
    void insufficientStockRejectsTheWholeSale() throws Exception {
        String token = sellerToken();
        Long available = createPricedProduct("1", "0");
        Long scarce = createPricedProduct("1", "0");
        addStock(available, 5);
        addStock(scarce, 1);
        UUID key = UUID.randomUUID();

        postJson("/api/v1/sales", """
                {"paymentMethod": "CASH", "items": [
                    {"productId": %d, "quantity": 2},
                    {"productId": %d, "quantity": 2}
                ]}
                """.formatted(available, scarce), token, key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Insufficient stock for product: " + scarce));

        // The decrement of the first line was rolled back with the rest of the sale.
        assertThat(stock(available)).isEqualTo(5);
        assertThat(stock(scarce)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sale WHERE idempotency_key = ?", Integer.class, key)).isZero();

        // The failed attempt was rolled back, so the same key works once the cart is fixed.
        postJson("/api/v1/sales", saleBody(available, 2), token, key).andExpect(status().isCreated());
        assertThat(stock(available)).isEqualTo(3);
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheOriginalSaleWithoutChargingTwice() throws Exception {
        String token = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 5);
        UUID key = UUID.randomUUID();
        String body = saleBody(product, 2);

        Long firstId = readId(postJson("/api/v1/sales", body, token, key).andExpect(status().isCreated()));
        postJson("/api/v1/sales", body, token, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        assertThat(stock(product)).isEqualTo(3);
    }

    @Test
    void reusingAKeyWithDifferentContentOrAnotherSellerConflicts() throws Exception {
        String token = sellerToken();
        Long product = createPricedProduct("1", "0");
        Long other = createPricedProduct("1", "0");
        addStock(product, 10);
        addStock(other, 10);
        UUID key = UUID.randomUUID();
        postJson("/api/v1/sales", saleBody(product, 1), token, key).andExpect(status().isCreated());

        List<String> differentBodies = List.of(
                saleBody(product, 2),
                saleBody(other, 1),
                twoItemBody(product, other),
                """
                {"paymentMethod": "CARD", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(product),
                """
                {"customerId": %d, "paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(createCustomer(), product));
        for (String body : differentBodies) {
            postJson("/api/v1/sales", body, token, key).andExpect(status().isConflict());
        }
        postJson("/api/v1/sales", saleBody(product, 1), sellerToken(), key).andExpect(status().isConflict());

        assertThat(stock(product)).isEqualTo(9);
    }

    @Test
    void concurrentRetriesWithTheSameKeyRecordOneSale() throws Exception {
        String token = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 10);
        String body = saleBody(product, 4);
        UUID key = UUID.randomUUID();
        int retries = 8;

        List<Integer> statuses = runConcurrently(Collections.nCopies(retries,
                () -> httpStatus(postJson("/api/v1/sales", body, token, key))));

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(retries - 1);
        assertThat(stock(product)).isEqualTo(6);
    }

    @Test
    void crossOrderedConcurrentSalesDoNotDeadlock() throws Exception {
        String token = sellerToken();
        Long a = createPricedProduct("1", "0");
        Long b = createPricedProduct("1", "0");
        int sales = 10;
        addStock(a, sales);
        addStock(b, sales);
        String ab = twoItemBody(a, b);
        String ba = twoItemBody(b, a);

        // Without sorting items by product id, [A, B] vs [B, A] would deadlock.
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < sales; i++) {
            String body = i % 2 == 0 ? ab : ba;
            UUID key = UUID.randomUUID();
            tasks.add(() -> httpStatus(postJson("/api/v1/sales", body, token, key)));
        }

        assertThat(runConcurrently(tasks)).containsOnly(201);
        assertThat(stock(a)).isZero();
        assertThat(stock(b)).isZero();
    }

    @Test
    void salesPurchasesAndCancellationsOnTheSameProductsDoNotDeadlock() throws Exception {
        String adminToken = adminAccessToken();
        Long a = createPricedProduct("1", "0");
        Long b = createPricedProduct("1", "0");
        addStock(a, 20);
        addStock(b, 20);
        Long supplierId = createSupplier();
        int each = 4;
        List<Long> toCancel = new ArrayList<>();
        for (int i = 0; i < each; i++) {
            toCancel.add(readId(sell(twoItemBody(a, b), adminToken).andExpect(status().isCreated())));
        }
        String saleAb = twoItemBody(a, b);
        String purchaseBa = """
                {"supplierId": %d, "items": [
                    {"productId": %d, "quantity": 1, "unitCost": 1},
                    {"productId": %d, "quantity": 1, "unitCost": 1}
                ]}
                """.formatted(supplierId, b, a);
        String cancellation = cancelBody("Lock order test");

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < each; i++) {
            UUID key = UUID.randomUUID();
            Long saleId = toCancel.get(i);
            tasks.add(() -> httpStatus(postJson("/api/v1/sales", saleAb, adminToken, key)));
            tasks.add(() -> httpStatus(postJson("/api/v1/purchases", purchaseBa, adminToken)));
            tasks.add(() -> httpStatus(
                    postJson("/api/v1/sales/" + saleId + "/cancellation", cancellation, adminToken)));
        }

        assertThat(runConcurrently(tasks)).allMatch(s -> s == 200 || s == 201);
        // 20 - 4 prepared sales - 4 new sales + 4 purchases + 4 cancellations.
        assertThat(stock(a)).isEqualTo(20);
        assertThat(stock(b)).isEqualTo(20);
        // The stock ledger adds up to the current stock.
        for (Long product : List.of(a, b)) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT sum(quantity) FROM inventory_movement WHERE product_id = ?", Integer.class, product))
                    .isEqualTo(stock(product));
        }
    }

    @Test
    void malformedSalesAreRejectedWithoutChangingStock() throws Exception {
        String token = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 5);

        // Missing Idempotency-Key header.
        postJson("/api/v1/sales", saleBody(product, 1), token).andExpect(status().isBadRequest());
        sell("""
                {"paymentMethod": "CASH", "items": []}
                """, token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
        sell("""
                {"items": [{"productId": %d, "quantity": 1}]}
                """.formatted(product), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paymentMethod").exists());
        sell("""
                {"paymentMethod": "CHEQUE", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(product), token).andExpect(status().isBadRequest());
        sell(saleBody(product, 0), token).andExpect(status().isBadRequest());
        sell("""
                {"paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 1.5}]}
                """.formatted(product), token).andExpect(status().isBadRequest());

        assertThat(stock(product)).isEqualTo(5);
    }

    @Test
    void itemsMustBeDistinctExistingProducts() throws Exception {
        String token = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 5);

        sell("""
                {"paymentMethod": "CASH", "items": [
                    {"productId": %d, "quantity": 1},
                    {"productId": %d, "quantity": 1}
                ]}
                """.formatted(product, product), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Each product can appear only once per sale"));
        sell(saleBody(999999L, 1), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product not found: 999999"));

        assertThat(stock(product)).isEqualTo(5);
    }

    @Test
    void customerMustExistAndBeActive() throws Exception {
        String token = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 5);
        Long inactiveCustomer = createCustomer();
        putJson("/api/v1/customers/" + inactiveCustomer, """
                {"name": "Gone", "active": false, "version": 0}
                """, adminAccessToken()).andExpect(status().isOk());

        sell("""
                {"customerId": 999999, "paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(product), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Customer not found"));
        sell("""
                {"customerId": %d, "paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(inactiveCustomer, product), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Customer is inactive"));

        assertThat(stock(product)).isEqualTo(5);
    }

    @Test
    void totalAboveTheMaximumIsRejected() throws Exception {
        // 2 x 9,999,999,999.99 exceeds NUMERIC(12,2); rejected before touching stock.
        Long product = createPricedProduct("9999999999.99", "0");

        sell(saleBody(product, 2), sellerToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Sale total exceeds the maximum allowed"));
    }

    @Test
    void walkInCustomerMatchesWithOrWithoutItsIdOnRetry() throws Exception {
        String token = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 5);
        Long walkInId = jdbcTemplate.queryForObject(
                "SELECT id FROM customer WHERE tax_id = 'XAXX010101000'", Long.class);
        UUID key = UUID.randomUUID();

        Long saleId = readId(postJson("/api/v1/sales", saleBody(product, 1), token, key)
                .andExpect(status().isCreated()));
        postJson("/api/v1/sales", """
                {"customerId": %d, "paymentMethod": "CASH", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(walkInId, product), token, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saleId));
    }

    @Test
    void retryAfterCancellationReturnsTheCancelledSale() throws Exception {
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 1);
        UUID key = UUID.randomUUID();
        Long saleId = readId(postJson("/api/v1/sales", saleBody(product, 1), adminToken, key)
                .andExpect(status().isCreated()));
        postJson("/api/v1/sales/" + saleId + "/cancellation", cancelBody("Voided"), adminToken)
                .andExpect(status().isOk());

        postJson("/api/v1/sales", saleBody(product, 1), adminToken, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saleId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stock(product)).isEqualTo(1);
    }

    @Test
    void inactiveProductCannotBeSold() throws Exception {
        Long product = createPricedProduct("1", "0");
        addStock(product, 1);
        deactivateProduct(product);

        sell(saleBody(product, 1), sellerToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product is inactive: " + product));
    }

    @Test
    void adminCancelsSaleAndStockReturns() throws Exception {
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 5);
        Long saleId = readId(sell(saleBody(product, 3), sellerToken()).andExpect(status().isCreated()));

        postJson("/api/v1/sales/" + saleId + "/cancellation", cancelBody("  Customer returned it  "), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellation.reason").value("Customer returned it"))
                .andExpect(jsonPath("$.cancellation.cancelledAt").exists())
                .andExpect(jsonPath("$.cancellation.cancelledBy.name").exists());

        assertThat(stock(product)).isEqualTo(5);
        assertThat(movement(saleId, product, "SALE_CANCELLATION")).containsExactly(3, 5);
        postJson("/api/v1/sales/" + saleId + "/cancellation", cancelBody("Again"), adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Sale is already cancelled"));
        getJson("/api/v1/sales?status=CANCELLED&saleNumber=" + saleNumberInDb(saleId), adminToken)
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void concurrentCancellationsReturnStockOnce() throws Exception {
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 4);
        Long saleId = readId(sell(saleBody(product, 4), adminToken).andExpect(status().isCreated()));
        String body = cancelBody("Duplicate charge");
        int attempts = 6;

        List<Integer> statuses = runConcurrently(Collections.nCopies(attempts,
                () -> httpStatus(postJson("/api/v1/sales/" + saleId + "/cancellation", body, adminToken))));

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(attempts - 1);
        assertThat(stock(product)).isEqualTo(4);
    }

    @Test
    void cancellingReturnsStockEvenIfTheProductWasDeactivated() throws Exception {
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 2);
        Long saleId = readId(sell(saleBody(product, 2), adminToken).andExpect(status().isCreated()));
        deactivateProduct(product);

        postJson("/api/v1/sales/" + saleId + "/cancellation", cancelBody("Wrong item"), adminToken)
                .andExpect(status().isOk());
        assertThat(stock(product)).isEqualTo(2);
    }

    @Test
    void invalidCancellationsAreRejected() throws Exception {
        String adminToken = adminAccessToken();
        String sellerToken = sellerToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 1);
        Long saleId = readId(sell(saleBody(product, 1), sellerToken).andExpect(status().isCreated()));
        String url = "/api/v1/sales/" + saleId + "/cancellation";

        postJson(url, cancelBody("Mine"), sellerToken).andExpect(status().isForbidden());
        postJson(url, cancelBody("   "), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        postJson(url, cancelBody("x".repeat(256)), adminToken).andExpect(status().isBadRequest());
        postJson("/api/v1/sales/999999/cancellation", cancelBody("Nope"), adminToken)
                .andExpect(status().isNotFound());

        assertThat(stock(product)).isZero();
    }

    @Test
    void sellersOnlySeeTheirOwnSales() throws Exception {
        CreatedUser seller = createSeller();
        String sellerToken = sellerToken(seller);
        String otherToken = sellerToken();
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("1", "0");
        addStock(product, 2);
        Long own = readId(sell(saleBody(product, 1), sellerToken).andExpect(status().isCreated()));
        Long others = readId(sell(saleBody(product, 1), otherToken).andExpect(status().isCreated()));

        getJson("/api/v1/sales/" + own, sellerToken).andExpect(status().isOk());
        getJson("/api/v1/sales/" + others, sellerToken).andExpect(status().isNotFound());
        getJson("/api/v1/sales/" + others, adminToken).andExpect(status().isOk());
        getJson("/api/v1/sales/999999", adminToken).andExpect(status().isNotFound());

        // The sellerId filter can't widen a seller's view.
        String body = getJson("/api/v1/sales?size=100", sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].items").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(body, "$.content[*].seller.id")).containsOnly(seller.id().intValue());
        getJson("/api/v1/sales?sellerId=" + seller.id(), adminToken)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(own));
        getJson("/api/v1/sales?sellerId=" + seller.id() + "&from=2100-01-01T00:00:00Z", adminToken)
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void invalidDateRangesAreRejected() throws Exception {
        String token = adminAccessToken();

        getJson("/api/v1/sales?from=2025-02-01T00:00:00Z&to=2025-01-01T00:00:00Z", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'from' must be before 'to'"));
        getJson("/api/v1/sales?from=2025-01-01T00:00:00Z&to=2025-01-01T00:00:00Z", token)
                .andExpect(status().isBadRequest());
        // ISO instants accept signed years beyond 9999; queryParam avoids URL-encoding the "+".
        mockMvc.perform(withToken(get("/api/v1/sales").queryParam("to", "+999999999-12-31T00:00:00Z"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Dates must be between 2000-01-01T00:00:00Z and 9999-12-30T00:00:00Z"));
        getJson("/api/v1/sales?from=1999-12-31T00:00:00Z", token).andExpect(status().isBadRequest());
    }

    // [quantity, resulting_stock] of the movement.
    private List<Integer> movement(Long saleId, Long productId, String type) {
        return jdbcTemplate.queryForObject("""
                SELECT quantity, resulting_stock FROM inventory_movement
                WHERE sale_id = ? AND product_id = ? AND type = ?""",
                (rs, row) -> List.of(rs.getInt(1), rs.getInt(2)), saleId, productId, type);
    }

    private long saleNumberInDb(Long saleId) {
        return jdbcTemplate.queryForObject("SELECT sale_number FROM sale WHERE id = ?", Long.class, saleId);
    }

    private static long readSaleNumber(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.saleNumber")).longValue();
    }

    private static String saleBody(Long productId, int quantity) {
        return """
                {"paymentMethod": "CASH", "items": [{"productId": %d, "quantity": %d}]}
                """.formatted(productId, quantity);
    }

    private static String twoItemBody(Long first, Long second) {
        return """
                {"paymentMethod": "CASH", "items": [
                    {"productId": %d, "quantity": 1},
                    {"productId": %d, "quantity": 1}
                ]}
                """.formatted(first, second);
    }

    private static String cancelBody(String reason) {
        return """
                {"reason": "%s"}
                """.formatted(reason);
    }
}
