package com.irvin.inventory.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.config.StoreProperties;
import com.irvin.inventory.report.dto.SaleReportResponse;
import com.irvin.inventory.report.dto.SaleReportResponse.DailyTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.Totals;
import com.irvin.inventory.sale.SaleStatus;
import com.irvin.inventory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SaleReportService {

    // A year (leap years included): bounds the daily breakdown and the scanned rows.
    static final int MAX_DAYS = 366;
    // Absolute bounds: extreme years overflow Java dates or PostgreSQL timestamps (500 instead of 400).
    static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 30);
    // Seconds; JdbcTemplate turns the transaction timeout into a statement timeout.
    private static final int TIMEOUT_SECONDS = 10;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private static final Totals NO_SALES = new Totals(0, ZERO, ZERO, ZERO);

    private final SaleReportRepository saleReportRepository;
    private final StoreProperties storeProperties;
    private final TransactionTemplate snapshotTransaction;

    public SaleReportService(SaleReportRepository saleReportRepository, StoreProperties storeProperties,
            PlatformTransactionManager transactionManager) {
        this.saleReportRepository = saleReportRepository;
        this.storeProperties = storeProperties;
        // One snapshot for every query, so totals and breakdowns agree even while sales come in.
        // The timeout keeps a heavy report from holding a pool connection the checkout needs.
        this.snapshotTransaction = new TransactionTemplate(transactionManager);
        snapshotTransaction.setReadOnly(true);
        snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshotTransaction.setTimeout(TIMEOUT_SECONDS);
    }

    // Validated before the transaction starts, so invalid requests never take a pool connection.
    public SaleReportResponse salesReport(LocalDate from, LocalDate to, Long sellerId, CurrentUser viewer) {
        if (from.isBefore(MIN_DATE) || to.isAfter(MAX_DATE)) {
            throw new BadRequestException("Dates must be between " + MIN_DATE + " and " + MAX_DATE);
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BadRequestException("The period can't exceed " + MAX_DAYS + " days");
        }
        ZoneId zone = storeProperties.timeZone();
        // Both dates are inclusive calendar days in the store's time zone.
        SaleReportRepository.Period period = new SaleReportRepository.Period(from, to.plusDays(1), zone,
                // Sellers only get their own sales, whatever seller filter they sent.
                viewer.isAdmin() ? sellerId : viewer.id());
        return snapshotTransaction.execute(status -> buildReport(from, to, zone, period));
    }

    private SaleReportResponse buildReport(LocalDate from, LocalDate to, ZoneId zone,
            SaleReportRepository.Period period) {
        Map<SaleStatus, Totals> totals = saleReportRepository.totalsByStatus(period);
        Totals completed = totals.getOrDefault(SaleStatus.COMPLETED, NO_SALES);
        Map<LocalDate, DailyTotal> byDay = saleReportRepository.completedByDay(period);
        return new SaleReportResponse(
                from,
                to,
                zone.getId(),
                completed,
                totals.getOrDefault(SaleStatus.CANCELLED, NO_SALES),
                averageTicket(completed),
                saleReportRepository.completedByPaymentMethod(period),
                saleReportRepository.completedBySeller(period),
                saleReportRepository.topProducts(period),
                from.datesUntil(to.plusDays(1))
                        .map(day -> byDay.getOrDefault(day, new DailyTotal(day, 0, ZERO)))
                        .toList());
    }

    private static BigDecimal averageTicket(Totals completed) {
        return completed.saleCount() == 0
                ? ZERO
                : completed.total().divide(BigDecimal.valueOf(completed.saleCount()), MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
