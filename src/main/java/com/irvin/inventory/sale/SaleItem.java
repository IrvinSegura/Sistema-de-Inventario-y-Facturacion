package com.irvin.inventory.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.irvin.inventory.catalog.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

// Immutable once recorded (see Sale).
@Entity
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class SaleItem {

    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int quantity;

    // Snapshot of the product price at sale time.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Snapshot of the product VAT rate at sale time.
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tax;

    // Tax is rounded per line with HALF_UP, the same rule as ck_sale_item_tax (PostgreSQL ROUND).
    static SaleItem of(Product product, int quantity) {
        SaleItem item = new SaleItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        // Prices are stored as NUMERIC(12,2), so UNNECESSARY never rounds.
        item.setUnitPrice(product.getSalePrice().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        item.setTaxRate(product.getTaxRate());
        item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        item.setTax(item.getSubtotal().multiply(item.getTaxRate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        return item;
    }
}
