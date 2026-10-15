package com.irvin.inventory.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.irvin.inventory.purchasing.Purchase;
import com.irvin.inventory.purchasing.PurchaseItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record PurchaseResponse(
        Long id,
        Ref supplier,
        Ref recordedBy,
        Instant purchaseDate,
        BigDecimal total,
        List<Item> items) {

    public record Ref(Long id, String name) {
    }

    // Explicit name: springdoc names nested records by simple name, and four of them are "Item".
    @Schema(name = "PurchaseItemResponse")
    public record Item(Long productId, String sku, String name, int quantity, BigDecimal unitCost, BigDecimal subtotal) {

        static Item from(PurchaseItem item) {
            return new Item(item.getProduct().getId(), item.getProduct().getSku(), item.getProduct().getName(),
                    item.getQuantity(), item.getUnitCost(), item.getSubtotal());
        }
    }

    public static PurchaseResponse from(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                new Ref(purchase.getSupplier().getId(), purchase.getSupplier().getName()),
                new Ref(purchase.getUser().getId(), purchase.getUser().getName()),
                purchase.getPurchaseDate(),
                purchase.getTotal(),
                purchase.getItems().stream().map(Item::from).toList());
    }
}
