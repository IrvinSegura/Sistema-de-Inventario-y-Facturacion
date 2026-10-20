package com.irvin.inventory.sale.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.irvin.inventory.sale.PaymentMethod;
import com.irvin.inventory.sale.Sale;
import com.irvin.inventory.sale.SaleStatus;
import com.irvin.inventory.sale.dto.SaleResponse.Cancellation;
import com.irvin.inventory.sale.dto.SaleResponse.Ref;

// List view without items; GET /sales/{id} returns the full detail.
public record SaleSummaryResponse(
        Long id,
        Long saleNumber,
        Ref customer,
        Ref seller,
        Instant saleDate,
        PaymentMethod paymentMethod,
        SaleStatus status,
        BigDecimal total,
        // Null unless cancelled; inlined so a list of cancellations needs no detail calls.
        Cancellation cancellation) {

    public static SaleSummaryResponse from(Sale sale) {
        return new SaleSummaryResponse(
                sale.getId(),
                sale.getSaleNumber(),
                new Ref(sale.getCustomer().getId(), sale.getCustomer().getName()),
                new Ref(sale.getUser().getId(), sale.getUser().getName()),
                sale.getSaleDate(),
                sale.getPaymentMethod(),
                sale.getStatus(),
                sale.getTotal(),
                Cancellation.from(sale));
    }
}
