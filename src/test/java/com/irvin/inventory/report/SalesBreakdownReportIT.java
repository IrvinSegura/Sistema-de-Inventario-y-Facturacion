package com.irvin.inventory.report;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.irvin.inventory.AbstractIntegrationTest;
import com.irvin.inventory.config.StoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Profit, sales by product and sales by customer. The database is shared, so each test moves its sales
// to a day of the past nobody else uses and asks for that day only.
class SalesBreakdownReportIT extends AbstractIntegrationTest {

    @Autowired
    private StoreProperties storeProperties;

    @Test
    void managersGetTheGrossProfitOfTheCostedLines() throws Exception {
        CreatedUser seller = createSeller();
        String sellerToken = sellerToken(seller);
        Long product = createPricedProduct("10", "0.16");
        purchase(product, 10, "4.00");
        // 3 x 10.00 = 30.00 before VAT, cost 3 x 4.00 = 12.00.
        sell(saleBody(product, 3, null), sellerToken).andExpect(status().isCreated());
        String url = "/api/v1/sale-reports?from=" + today() + "&to=" + today();

        getJson(url + "&sellerId=" + seller.id(), adminAccessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profit.costedSubtotal").value(30.0))
                .andExpect(jsonPath("$.profit.cost").value(12.0))
                .andExpect(jsonPath("$.profit.grossProfit").value(18.0))
                .andExpect(jsonPath("$.profit.margin").value(0.6));
        // A seller's own report never carries costs.
        getJson(url, sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed.saleCount").value(1))
                .andExpect(jsonPath("$.profit").isEmpty());
    }

    @Test
    void listsSoldProductsBestFirstAndTheOnesThatDidntSell() throws Exception {
        String sellerToken = sellerToken();
        Long categoryId = createCategory("Breakdown " + UUID.randomUUID());
        Long best = createProduct(categoryId, 0);
        Long second = createProduct(categoryId, 0);
        Long unsold = createProduct(categoryId, 0);
        Long noStock = createProduct(categoryId, 0);
        Long newer = createProduct(categoryId, 0);
        purchase(best, 10, "4.00");
        purchase(second, 10, "1.00");
        purchase(unsold, 7, "1.00");
        purchase(newer, 2, "1.00");
        // createProduct prices at 10.00 without VAT.
        Long sale = readId(sell(twoLineBody(best, 3, second, 1), sellerToken).andExpect(status().isCreated()));
        // A sale recorded before costs were tracked: its cost is unknown, not zero.
        jdbcTemplate.update("UPDATE sale_item SET unit_cost = NULL WHERE sale_id = ? AND product_id = ?", sale, second);
        LocalDate day = moveToUnusedDay(sale);
        // Every product but newer already existed that day.
        jdbcTemplate.update("UPDATE product SET created_at = ? WHERE id IN (?, ?, ?, ?)",
                day.atStartOfDay(storeProperties.timeZone()).toOffsetDateTime(), best, second, unsold, noStock);
        String url = "/api/v1/sale-reports/by-product?from=" + day + "&to=" + day + "&categoryId=" + categoryId;

        getJson(url, adminAccessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].productId").value(best))
                .andExpect(jsonPath("$.content[0].quantity").value(3))
                .andExpect(jsonPath("$.content[0].subtotal").value(30.0))
                .andExpect(jsonPath("$.content[0].cost").value(12.0))
                .andExpect(jsonPath("$.content[0].grossProfit").value(18.0))
                .andExpect(jsonPath("$.content[0].averageCost").value(4.0))
                .andExpect(jsonPath("$.content[0].lastSaleDate").isNotEmpty())
                .andExpect(jsonPath("$.content[1].productId").value(second))
                .andExpect(jsonPath("$.content[1].cost").isEmpty())
                .andExpect(jsonPath("$.content[1].grossProfit").isEmpty());
        // Active, in stock and not sold that day; noStock has nothing waiting on the shelf and newer
        // wasn't in the catalog yet.
        getJson(url + "&unsold=true", adminAccessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(unsold))
                .andExpect(jsonPath("$.content[0].stock").value(7))
                .andExpect(jsonPath("$.content[0].quantity").value(0))
                .andExpect(jsonPath("$.content[0].lastSaleDate").isEmpty());
        // Pages never overlap.
        getJson(url + "&size=1&page=1", adminAccessToken())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(second));
        assertNoSuchProduct(noStock, url + "&unsold=true");
        assertNoSuchProduct(newer, url + "&unsold=true");
    }

    @Test
    void listsRegisteredCustomersBestFirstWithoutTheWalkInOne() throws Exception {
        String sellerToken = sellerToken();
        Long product = createPricedProduct("10", "0");
        purchase(product, 10, "1.00");
        Long big = createCustomer();
        Long small = createCustomer();
        Long first = readId(sell(saleBody(product, 3, big), sellerToken).andExpect(status().isCreated()));
        Long second = readId(sell(saleBody(product, 2, big), sellerToken).andExpect(status().isCreated()));
        Long third = readId(sell(saleBody(product, 1, small), sellerToken).andExpect(status().isCreated()));
        Long walkIn = readId(sell(saleBody(product, 4, null), sellerToken).andExpect(status().isCreated()));
        LocalDate day = moveToUnusedDay(first, second, third, walkIn);

        getJson("/api/v1/sale-reports/by-customer?from=" + day + "&to=" + day, adminAccessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].customerId").value(big))
                .andExpect(jsonPath("$.content[0].saleCount").value(2))
                .andExpect(jsonPath("$.content[0].total").value(50.0))
                .andExpect(jsonPath("$.content[0].averageTicket").value(25.0))
                .andExpect(jsonPath("$.content[1].customerId").value(small));
    }

    @Test
    void breakdownsAreForManagersAndValidateThePeriod() throws Exception {
        String sellerToken = sellerToken();
        String period = "?from=" + today() + "&to=" + today();

        getJson("/api/v1/sale-reports/by-product" + period, sellerToken).andExpect(status().isForbidden());
        getJson("/api/v1/sale-reports/by-customer" + period, sellerToken).andExpect(status().isForbidden());
        getJson("/api/v1/sale-reports/by-product?from=2024-01-01&to=2025-01-01", adminAccessToken())
                .andExpect(status().isBadRequest());
        getJson("/api/v1/sale-reports/by-customer?from=" + today() + "&to=" + today().minusDays(1), adminAccessToken())
                .andExpect(status().isBadRequest());
    }

    private void assertNoSuchProduct(Long productId, String url) throws Exception {
        getJson(url, adminAccessToken())
                .andExpect(jsonPath("$.content[?(@.productId == " + productId + ")]").isEmpty());
    }

    // Noon of a random day in 2001-2009, in the store's zone: far from every other test's sales.
    private LocalDate moveToUnusedDay(Long... saleIds) {
        LocalDate day = LocalDate.of(2001, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(3000));
        OffsetDateTime noon = day.atTime(12, 0).atZone(storeProperties.timeZone()).toOffsetDateTime();
        for (Long saleId : saleIds) {
            jdbcTemplate.update("UPDATE sale SET sale_date = ? WHERE id = ?", noon, saleId);
        }
        return day;
    }

    private void purchase(Long productId, int quantity, String unitCost) throws Exception {
        postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": [{"productId": %d, "quantity": %d, "unitCost": %s}]}
                """.formatted(createSupplier(), productId, quantity, unitCost), adminAccessToken())
                .andExpect(status().isCreated());
    }

    private LocalDate today() {
        return LocalDate.now(storeProperties.timeZone());
    }

    private static String saleBody(Long productId, int quantity, Long customerId) {
        return """
                {"paymentMethod": "CASH", "customerId": %s, "items": [{"productId": %d, "quantity": %d}]}
                """.formatted(customerId, productId, quantity);
    }

    private static String twoLineBody(Long first, int firstQuantity, Long second, int secondQuantity) {
        return """
                {"paymentMethod": "CARD", "items": [
                    {"productId": %d, "quantity": %d},
                    {"productId": %d, "quantity": %d}
                ]}
                """.formatted(first, firstQuantity, second, secondQuantity);
    }
}
