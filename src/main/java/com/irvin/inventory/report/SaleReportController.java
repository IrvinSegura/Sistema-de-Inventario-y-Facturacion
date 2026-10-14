package com.irvin.inventory.report;

import java.time.LocalDate;

import com.irvin.inventory.report.dto.SaleReportResponse;
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
}
