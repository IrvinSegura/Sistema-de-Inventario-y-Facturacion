package com.irvin.inventory.sale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.irvin.inventory.sale.PaymentMethod;
import com.irvin.inventory.sale.Sale;
import com.irvin.inventory.sale.SaleItem;
import com.irvin.inventory.sale.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record SaleResponse(
        Long id,
        Long saleNumber,
        Ref customer,
        Ref seller,
        Instant saleDate,
        PaymentMethod paymentMethod,
        SaleStatus status,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        // Null unless the sale is cancelled.
        Cancellation cancellation,
        List<Item> items) {

    public record Ref(Long id, String name) {
    }

    public record Cancellation(Instant cancelledAt, Ref cancelledBy, String reason) {

        // Null unless the sale is cancelled; needs cancelledBy loaded.
        static Cancellation from(Sale sale) {
            return sale.getStatus() == SaleStatus.CANCELLED
                    ? new Cancellation(sale.getCancelledAt(),
                            new Ref(sale.getCancelledBy().getId(), sale.getCancelledBy().getName()),
                            sale.getCancellationReason())
                    : null;
        }
    }

    // Explicit name: springdoc names nested records by simple name, and four of them are "Item".
    @Schema(name = "SaleItemResponse")
    public record Item(
            Long productId, String sku, String name, int quantity,
            BigDecimal unitPrice, BigDecimal taxRate, BigDecimal subtotal, BigDecimal tax) {

        static Item from(SaleItem item) {
            return new Item(item.getProduct().getId(), item.getProduct().getSku(), item.getProduct().getName(),
                    item.getQuantity(), item.getUnitPrice(), item.getTaxRate(), item.getSubtotal(), item.getTax());
        }
    }

    public static SaleResponse from(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getSaleNumber(),
                new Ref(sale.getCustomer().getId(), sale.getCustomer().getName()),
                new Ref(sale.getUser().getId(), sale.getUser().getName()),
                sale.getSaleDate(),
                sale.getPaymentMethod(),
                sale.getStatus(),
                sale.getSubtotal(),
                sale.getTax(),
                sale.getTotal(),
                Cancellation.from(sale),
                sale.getItems().stream().map(Item::from).toList());
    }
}
