package com.irvin.inventory.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.irvin.inventory.config.StoreProperties;
import com.irvin.inventory.report.dto.PurchaseReportResponse;
import com.irvin.inventory.report.dto.PurchaseReportResponse.DailyTotal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PurchaseReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final PurchaseReportRepository purchaseReportRepository;
    private final StoreProperties storeProperties;
    private final TransactionTemplate snapshotTransaction;

    public PurchaseReportService(PurchaseReportRepository purchaseReportRepository, StoreProperties storeProperties,
            PlatformTransactionManager transactionManager) {
        this.purchaseReportRepository = purchaseReportRepository;
        this.storeProperties = storeProperties;
        this.snapshotTransaction = ReportPeriods.snapshot(transactionManager);
    }

    // Same periods as the sales report: inclusive days in the store's time zone, up to a year.
    public PurchaseReportResponse report(LocalDate from, LocalDate to, Long supplierId) {
        ReportPeriods.requireValid(from, to);
        PurchaseReportRepository.Period period =
                new PurchaseReportRepository.Period(from, to.plusDays(1), storeProperties.timeZone(), supplierId);
        return snapshotTransaction.execute(status -> build(from, to, period));
    }

    private PurchaseReportResponse build(LocalDate from, LocalDate to, PurchaseReportRepository.Period period) {
        PurchaseReportRepository.Totals totals = purchaseReportRepository.totals(period);
        Map<LocalDate, DailyTotal> byDay = purchaseReportRepository.byDay(period);
        return new PurchaseReportResponse(
                from,
                to,
                period.zone().getId(),
                period.supplierId(),
                totals.purchaseCount(),
                purchaseReportRepository.units(period),
                totals.total(),
                purchaseReportRepository.bySupplier(period),
                purchaseReportRepository.topProducts(period),
                from.datesUntil(to.plusDays(1))
                        .map(day -> byDay.getOrDefault(day, new DailyTotal(day, 0, ZERO)))
                        .toList());
    }
}
