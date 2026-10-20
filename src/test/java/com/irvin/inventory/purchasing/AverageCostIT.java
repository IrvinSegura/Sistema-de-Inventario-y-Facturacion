package com.irvin.inventory.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.irvin.inventory.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class AverageCostIT extends AbstractIntegrationTest {

    @Test
    void purchasesAverageTheCostAndSalesSnapshotIt() throws Exception {
        String sellerToken = sellerToken();
        Long product = createPricedProduct("10", "0");
        assertThat(averageCost(product)).isNull();

        purchase(product, 10, "4.00");
        assertThat(averageCost(product)).isEqualByComparingTo("4");
        // (10 x 4.00 + 30 x 6.00) / 40 = 5.50.
        purchase(product, 30, "6.00");
        assertThat(averageCost(product)).isEqualByComparingTo("5.5");

        Long sale = readId(sell(saleBody(product, 5), sellerToken).andExpect(status().isCreated()));
        assertThat(unitCost(sale, product)).isEqualByComparingTo("5.5");
        // Selling doesn't change the average of the units left.
        assertThat(averageCost(product)).isEqualByComparingTo("5.5");

        // Without units on hand the next purchase sets the cost on its own.
        sell(saleBody(product, 35), sellerToken).andExpect(status().isCreated());
        purchase(product, 2, "9.00");
        assertThat(averageCost(product)).isEqualByComparingTo("9");
    }

    @Test
    void averageKeepsFourDecimals() throws Exception {
        Long product = createPricedProduct("10", "0");
        purchase(product, 1, "1.00");
        purchase(product, 2, "2.00");

        // 5 / 3 = 1.6666... -> 1.6667.
        assertThat(averageCost(product)).isEqualTo(new BigDecimal("1.6667"));
    }

    @Test
    void productsNeverPurchasedSellWithoutACost() throws Exception {
        Long product = createPricedProduct("10", "0");
        // An adjustment adds units without a cost.
        postJson("/api/v1/stock-adjustments", """
                {"productId": %d, "quantity": 3, "reason": "Initial count"}
                """.formatted(product), adminAccessToken()).andExpect(status().isCreated());

        Long sale = readId(sell(saleBody(product, 1), sellerToken()).andExpect(status().isCreated()));

        assertThat(unitCost(sale, product)).isNull();
    }

    private void purchase(Long productId, int quantity, String unitCost) throws Exception {
        postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": [{"productId": %d, "quantity": %d, "unitCost": %s}]}
                """.formatted(createSupplier(), productId, quantity, unitCost), adminAccessToken())
                .andExpect(status().isCreated());
    }

    private BigDecimal averageCost(Long productId) {
        return jdbcTemplate.queryForObject("SELECT average_cost FROM product WHERE id = ?", BigDecimal.class, productId);
    }

    private BigDecimal unitCost(Long saleId, Long productId) {
        return jdbcTemplate.queryForObject("SELECT unit_cost FROM sale_item WHERE sale_id = ? AND product_id = ?",
                BigDecimal.class, saleId, productId);
    }

    private static String saleBody(Long productId, int quantity) {
        return """
                {"paymentMethod": "CASH", "items": [{"productId": %d, "quantity": %d}]}
                """.formatted(productId, quantity);
    }
}
