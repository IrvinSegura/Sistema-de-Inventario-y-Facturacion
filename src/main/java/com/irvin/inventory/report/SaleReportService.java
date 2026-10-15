package com.irvin.inventory.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.irvin.inventory.common.DateBounds;
import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.config.StoreProperties;
import com.irvin.inventory.report.SaleReportRepository.PaymentMethodAmount;
import com.irvin.inventory.report.dto.CashClosingResponse;
import com.irvin.inventory.report.dto.CashClosingResponse.Amounts;
import com.irvin.inventory.report.dto.SaleReportResponse;
import com.irvin.inventory.report.dto.SaleReportResponse.DailyTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.Totals;
import com.irvin.inventory.sale.PaymentMethod;
import com.irvin.inventory.sale.SaleService;
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
    // A sale stamped just before midnight commits at most this long after it; twice the timeout of
    // sale and cancellation transactions, so a closed day really has every movement.
    static final Duration CLOSING_MARGIN = Duration.ofSeconds(2L * SaleService.TRANSACTION_TIMEOUT_SECONDS);
    // Seconds; JdbcTemplate turns the transaction timeout into a statement timeout.
    private static final int TIMEOUT_SECONDS = 10;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private static final Totals NO_SALES = new Totals(0, ZERO, ZERO, ZERO);

    private final SaleReportRepository saleReportRepository;
    private final StoreProperties storeProperties;
    private final Clock clock;
    private final TransactionTemplate snapshotTransaction;

    public SaleReportService(SaleReportRepository saleReportRepository, StoreProperties storeProperties,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.saleReportRepository = saleReportRepository;
        this.storeProperties = storeProperties;
        this.clock = clock;
        // One snapshot for every query, so totals and breakdowns agree even while sales come in.
        // The timeout keeps a heavy report from holding a pool connection the checkout needs.
        this.snapshotTransaction = new TransactionTemplate(transactionManager);
        snapshotTransaction.setReadOnly(true);
        snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshotTransaction.setTimeout(TIMEOUT_SECONDS);
    }

    // Both reports validate before the transaction starts, so invalid requests never take a pool connection.
    public SaleReportResponse salesReport(LocalDate from, LocalDate to, Long sellerId, CurrentUser viewer) {
        DateBounds.requireWithin(from, to);
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BadRequestException("The period can't exceed " + MAX_DAYS + " days");
        }
        ZoneId zone = storeProperties.timeZone();
        // Both dates are inclusive calendar days in the store's time zone.
        SaleReportRepository.Period period =
                new SaleReportRepository.Period(from, to.plusDays(1), zone, scopedSellerId(sellerId, viewer));
        return snapshotTransaction.execute(status -> buildSalesReport(from, to, zone, period));
    }

    // date defaults to today in the store's time zone.
    public CashClosingResponse cashClosing(LocalDate date, Long sellerId, CurrentUser viewer) {
        ZoneId zone = storeProperties.timeZone();
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(zone));
        DateBounds.requireWithin(day);
        SaleReportRepository.Period period =
                new SaleReportRepository.Period(day, day.plusDays(1), zone, scopedSellerId(sellerId, viewer));
        Instant closesAt = day.plusDays(1).atStartOfDay(zone).toInstant().plus(CLOSING_MARGIN);
        boolean closed = !clock.instant().isBefore(closesAt);
        return snapshotTransaction.execute(status -> buildCashClosing(day, zone, closed, period));
    }

    // Sellers only get their own sales (and, in the cash closing, the cancellations of those sales,
    // whoever made them), whatever seller filter they sent.
    private static Long scopedSellerId(Long sellerId, CurrentUser viewer) {
        return viewer.isAdmin() ? sellerId : viewer.id();
    }

    private SaleReportResponse buildSalesReport(LocalDate from, LocalDate to, ZoneId zone,
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

    private CashClosingResponse buildCashClosing(LocalDate day, ZoneId zone, boolean closed,
            SaleReportRepository.Period period) {
        Map<PaymentMethod, PaymentMethodAmount> sales =
                indexByPaymentMethod(saleReportRepository.salesByPaymentMethod(period));
        Map<PaymentMethod, PaymentMethodAmount> cancellations =
                indexByPaymentMethod(saleReportRepository.cancellationsByPaymentMethod(period));
        // EnumMap: every payment method, serialized in declaration order.
        Map<PaymentMethod, Amounts> byPaymentMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod method : PaymentMethod.values()) {
            byPaymentMethod.put(method, toAmounts(sales.get(method), cancellations.get(method)));
        }
        Amounts total = byPaymentMethod.values().stream().reduce(Amounts.NONE, Amounts::plus);
        return new CashClosingResponse(day, zone.getId(), period.sellerId(), closed, byPaymentMethod, total,
                byPaymentMethod.get(PaymentMethod.CASH).net());
    }

    // A missing amount means no movements with that payment method.
    private static Amounts toAmounts(PaymentMethodAmount sales, PaymentMethodAmount cancellations) {
        return Amounts.of(
                sales == null ? 0 : sales.count(),
                sales == null ? ZERO : sales.total(),
                cancellations == null ? 0 : cancellations.count(),
                cancellations == null ? ZERO : cancellations.total());
    }

    private static Map<PaymentMethod, PaymentMethodAmount> indexByPaymentMethod(List<PaymentMethodAmount> amounts) {
        Map<PaymentMethod, PaymentMethodAmount> map = new EnumMap<>(PaymentMethod.class);
        amounts.forEach(amount -> map.put(amount.paymentMethod(), amount));
        return map;
    }
}
