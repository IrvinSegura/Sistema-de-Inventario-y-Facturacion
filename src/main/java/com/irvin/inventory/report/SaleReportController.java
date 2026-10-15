package com.irvin.inventory.report;

import java.time.LocalDate;

import com.irvin.inventory.report.dto.CashClosingResponse;
import com.irvin.inventory.report.dto.SaleReportResponse;
import com.irvin.inventory.sale.SaleService;
import com.irvin.inventory.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reports", description = "Any user; sellers only get their own sales in sale reports")
@RestController
@RequestMapping("/api/v1/sale-reports")
@RequiredArgsConstructor
public class SaleReportController {

    private final SaleReportService saleReportService;

    @Operation(summary = "Sales summary for a period",
            description = "Totals, payment methods, sellers, top 10 products and a daily breakdown of completed "
                    + "sales; cancelled sales are counted separately, on the day of the sale (not of the "
                    + "cancellation). Dates are calendar days in the store's time zone, both inclusive, up to 366 "
                    + "days, between 2000-01-01 and 9999-12-30.")
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

    @Operation(summary = "Cash closing for one day, by payment method",
            description = "Sales rung up that day (even if cancelled later) minus cancellations made that day "
                    + "(even of older sales), plus the day's cash drawers; expectedCash is their opening floats "
                    + "plus the net cash. Nets can be negative when cancellations exceed sales. 'closed' turns true "
                    + 2 * SaleService.TRANSACTION_TIMEOUT_SECONDS + " s after the day ends; until then, "
                    + "sales still being recorded can change the figures. Sellers get their own drawer: their "
                    + "sales and the cancellations of their sales, only once their drawer for that day is "
                    + "closed (blind count, 409 before).")
    @GetMapping("/cash-closing")
    public CashClosingResponse cashClosing(
            @Parameter(description = "Day in the store's time zone; defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Ignored for sellers") @RequestParam(required = false) Long sellerId,
            @AuthenticationPrincipal Jwt jwt) {
        return saleReportService.cashClosing(date, sellerId, CurrentUser.from(jwt));
    }
}
