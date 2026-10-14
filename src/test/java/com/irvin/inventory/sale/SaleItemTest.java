package com.irvin.inventory.sale;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.irvin.inventory.catalog.Product;
import org.junit.jupiter.api.Test;

class SaleItemTest {

    @Test
    void snapshotsPriceAndTaxRateAndComputesLineAmounts() {
        SaleItem item = SaleItem.of(product("10.03", "0.1600"), 3);

        assertThat(item.getUnitPrice()).isEqualByComparingTo("10.03");
        assertThat(item.getTaxRate()).isEqualByComparingTo("0.16");
        assertThat(item.getSubtotal()).isEqualTo(new BigDecimal("30.09"));
        // 30.09 * 0.16 = 4.8144
        assertThat(item.getTax()).isEqualTo(new BigDecimal("4.81"));
    }

    @Test
    void roundsLineTaxHalfUp() {
        // 0.10 * 0.05 = 0.005: HALF_UP gives 0.01 (HALF_EVEN would give 0.00), matching PostgreSQL ROUND.
        assertThat(SaleItem.of(product("0.10", "0.0500"), 1).getTax()).isEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void zeroRateProductsPayNoTax() {
        SaleItem item = SaleItem.of(product("25.5", "0.0000"), 2);

        assertThat(item.getSubtotal()).isEqualTo(new BigDecimal("51.00"));
        assertThat(item.getTax()).isEqualTo(new BigDecimal("0.00"));
    }

    private static Product product(String salePrice, String taxRate) {
        Product product = new Product();
        product.setSalePrice(new BigDecimal(salePrice));
        product.setTaxRate(new BigDecimal(taxRate));
        return product;
    }
}
