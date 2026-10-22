package com.irvin.inventory.report;

import java.time.LocalDate;

import com.irvin.inventory.report.dto.PurchaseReportResponse;
import com.irvin.inventory.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Description lives on SaleReportController; springdoc merges tags with the same name.
@Tag(name = "Reports")
@RestController
@RequestMapping("/api/v1/purchase-reports")
@PreAuthorize(Roles.MANAGERS)
@RequiredArgsConstructor
public class PurchaseReportController {

    private final PurchaseReportService purchaseReportService;

    @Operation(summary = "Purchases summary for a period",
            description = "Totals, units, suppliers, the 10 products with the most money spent and a daily "
                    + "breakdown. Dates are calendar days in the store's time zone, both inclusive, up to 366 "
                    + "days, between 2000-01-01 and 9999-12-30.")
    @GetMapping
    public PurchaseReportResponse purchasesReport(
            @Parameter(description = "First day, e.g. 2026-10-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day (inclusive), e.g. 2026-10-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long supplierId) {
        return purchaseReportService.report(from, to, supplierId);
    }
}
