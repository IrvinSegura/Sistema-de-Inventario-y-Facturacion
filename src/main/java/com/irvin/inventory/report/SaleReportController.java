package com.irvin.inventory.report;

import java.time.LocalDate;

import com.irvin.inventory.report.dto.CashClosingResponse;
import com.irvin.inventory.report.dto.CustomerSalesResponse;
import com.irvin.inventory.report.dto.ProductSalesResponse;
import com.irvin.inventory.report.dto.SaleReportResponse;
import com.irvin.inventory.sale.SaleService;
import com.irvin.inventory.security.CurrentUser;
import com.irvin.inventory.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reports", description = "Sales summary, cash closing and low stock: any user, sellers only get "
        + "their own sales. Sales by product and customer, and purchase reports: ADMIN or SUPERVISOR")
@RestController
@RequestMapping("/api/v1/sale-reports")
@RequiredArgsConstructor
public class SaleReportController {

    private final SaleReportService saleReportService;

    @Operation(summary = "Sales summary for a period",
            description = "Totals, payment methods, sellers, top 10 products and a daily breakdown of completed "
                    + "sales; cancelled sales are counted separately, on the day of the sale (not of the "
                    + "cancellation). Dates are calendar days in the store's time zone, both inclusive, up to 366 "
                    + "days, between 2000-01-01 and 9999-12-30. 'profit' (gross profit of the lines with a known "
                    + "cost) is for managers only; it is null for sellers.")
    @GetMapping
    public SaleReportResponse salesReport(
            @Parameter(description = "First day, e.g. 2026-10-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day (inclusive), e.g. 2026-10-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Ignored for sellers") @RequestParam(required = false) Long sellerId,
            @AuthenticationPrincipal Jwt jwt) {
        return saleReportService.salesReport(from, to, sellerId, CurrentUser.from(jwt));
    }

    @Operation(summary = "Cash closing for one day or one drawer, by payment method and seller",
            description = "A day: sales rung up that day (even if cancelled later) minus cancellations made that "
                    + "day (even of older sales), plus the day's cash drawers; expectedCash is their opening "
                    + "floats plus the net cash. 'closed' turns true "
                    + 2 * SaleService.TRANSACTION_TIMEOUT_SECONDS + " s after the day ends; until then, "
                    + "sales still being recorded can change the figures. A drawer: the sales recorded in it "
                    + "minus the refunds that came out of it, final once it is closed. Nets can be negative "
                    + "when cancellations exceed sales. Sellers get their own sales (and the cancellations of "
                    + "them) for a day, or a drawer they're in (404 otherwise), only once their drawer is "
                    + "closed (blind count, 409 before).")
    @GetMapping("/cash-closing")
    public CashClosingResponse cashClosing(
            @Parameter(description = "Day in the store's time zone; defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Ignored for sellers") @RequestParam(required = false) Long sellerId,
            @Parameter(description = "One drawer instead of a day; date and sellerId are then ignored")
            @RequestParam(required = false) Long cashDrawerId,
            @AuthenticationPrincipal Jwt jwt) {
        return saleReportService.cashClosing(date, sellerId, cashDrawerId, CurrentUser.from(jwt));
    }

    @Operation(summary = "Completed sales by product for a period, or the products that didn't sell",
            description = "Products sold in the period, best first by amount before VAT, with the cost of the "
                    + "units sold and the gross profit when every unit has a recorded cost. With unsold=true: "
                    + "active products with stock and no completed sale in the period, most units first. Same "
                    + "dates as the sales summary; the sort parameter is ignored.")
    @PreAuthorize(Roles.MANAGERS)
    @GetMapping("/by-product")
    public Page<ProductSalesResponse> salesByProduct(
            @Parameter(description = "First day, e.g. 2026-10-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day (inclusive), e.g. 2026-10-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Products that didn't sell instead of the ones that did")
            @RequestParam(defaultValue = "false") boolean unsold,
            @ParameterObject Pageable pageable) {
        return saleReportService.salesByProduct(from, to, categoryId, unsold, pageable);
    }

    @Operation(summary = "Completed sales by registered customer for a period, best first",
            description = "The walk-in customer is left out. Same dates as the sales summary; the sort "
                    + "parameter is ignored.")
    @PreAuthorize(Roles.MANAGERS)
    @GetMapping("/by-customer")
    public Page<CustomerSalesResponse> salesByCustomer(
            @Parameter(description = "First day, e.g. 2026-10-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day (inclusive), e.g. 2026-10-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject Pageable pageable) {
        return saleReportService.salesByCustomer(from, to, pageable);
    }
}
