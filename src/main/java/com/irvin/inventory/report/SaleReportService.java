package com.irvin.inventory.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.irvin.inventory.common.DateBounds;
import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.config.StoreProperties;
import com.irvin.inventory.report.SaleReportRepository.SellerPaymentAmount;
import com.irvin.inventory.report.dto.CashClosingResponse;
import com.irvin.inventory.report.dto.CashClosingResponse.Amounts;
import com.irvin.inventory.report.dto.CashClosingResponse.SellerAmounts;
import com.irvin.inventory.report.dto.SaleReportResponse;
import com.irvin.inventory.report.dto.SaleReportResponse.DailyTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.Totals;
import com.irvin.inventory.sale.CashDrawerService;
import com.irvin.inventory.sale.PaymentMethod;
import com.irvin.inventory.sale.dto.CashDrawerResponse;
import com.irvin.inventory.sale.dto.SaleResponse.Ref;
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
    private final CashDrawerService cashDrawerService;
    private final StoreProperties storeProperties;
    private final Clock clock;
    private final TransactionTemplate snapshotTransaction;

    public SaleReportService(SaleReportRepository saleReportRepository, CashDrawerService cashDrawerService,
            StoreProperties storeProperties, Clock clock, PlatformTransactionManager transactionManager) {
        this.saleReportRepository = saleReportRepository;
        this.cashDrawerService = cashDrawerService;
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

    // date defaults to today in the store's time zone; a drawer replaces both date and seller.
    public CashClosingResponse cashClosing(LocalDate date, Long sellerId, Long cashDrawerId, CurrentUser viewer) {
        ZoneId zone = storeProperties.timeZone();
        if (cashDrawerId != null) {
            return snapshotTransaction.execute(status -> buildDrawerClosing(cashDrawerId, zone, viewer));
        }
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(zone));
        DateBounds.requireWithin(day);
        SaleReportRepository.Period period =
                new SaleReportRepository.Period(day, day.plusDays(1), zone, scopedSellerId(sellerId, viewer));
        Instant closesAt = day.plusDays(1).atStartOfDay(zone).toInstant().plus(CLOSING_MARGIN);
        boolean closed = !clock.instant().isBefore(closesAt);
        return snapshotTransaction.execute(status -> buildCashClosing(day, zone, closed, period, viewer));
    }

    // Sellers only get their own sales (and, in the cash closing, the cancellations of those sales,
    // whoever made them), whatever seller filter they sent.
    private static Long scopedSellerId(Long sellerId, CurrentUser viewer) {
        return viewer.isManager() ? sellerId : viewer.id();
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
            SaleReportRepository.Period period, CurrentUser viewer) {
        // Same snapshot: the drawer reads join the template's transaction.
        List<CashDrawerResponse> drawers = cashDrawerService.findByDay(day, period.sellerId());
        // Blind count: a seller who knew the expected cash could declare exactly that and pocket any
        // surplus. Hiding only the cash figures isn't enough, since they follow from the other ones.
        if (!viewer.isManager() && drawers.stream().anyMatch(drawer -> drawer.closing() == null)) {
            throw blindCount();
        }
        return cashClosing(day, zone, period.sellerId(), null, closed, drawers,
                saleReportRepository.salesBySeller(period), saleReportRepository.cancellationsBySeller(period));
    }

    // Sellers only get a drawer they're in (404 otherwise), once it is closed.
    private CashClosingResponse buildDrawerClosing(Long drawerId, ZoneId zone, CurrentUser viewer) {
        CashDrawerResponse drawer = cashDrawerService.findById(drawerId, viewer);
        boolean closed = drawer.closing() != null;
        if (!viewer.isManager() && !closed) {
            throw blindCount();
        }
        return cashClosing(drawer.businessDate(), zone, null, drawerId, closed, List.of(drawer),
                saleReportRepository.drawerSalesBySeller(drawerId),
                saleReportRepository.drawerCancellationsBySeller(drawerId));
    }

    private static CashClosingResponse cashClosing(LocalDate day, ZoneId zone, Long sellerId, Long drawerId,
            boolean closed, List<CashDrawerResponse> drawers, List<SellerPaymentAmount> sales,
            List<SellerPaymentAmount> cancellations) {
        Map<Long, Ref> sellers = new LinkedHashMap<>();
        Map<Long, Map<PaymentMethod, Amounts>> bySellerAndMethod = new HashMap<>();
        sales.forEach(sale -> add(sellers, bySellerAndMethod, sale,
                Amounts.of(sale.count(), sale.total(), 0, ZERO)));
        cancellations.forEach(cancellation -> add(sellers, bySellerAndMethod, cancellation,
                Amounts.of(0, ZERO, cancellation.count(), cancellation.total())));

        // EnumMap: every payment method, serialized in declaration order.
        Map<PaymentMethod, Amounts> byPaymentMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod method : PaymentMethod.values()) {
            byPaymentMethod.put(method, bySellerAndMethod.values().stream()
                    .map(amounts -> amounts.getOrDefault(method, Amounts.NONE))
                    .reduce(Amounts.NONE, Amounts::plus));
        }
        Amounts total = byPaymentMethod.values().stream().reduce(Amounts.NONE, Amounts::plus);
        List<SellerAmounts> bySeller = sellers.values().stream()
                .sorted(Comparator.comparing(Ref::name).thenComparing(Ref::id))
                .map(seller -> {
                    Map<PaymentMethod, Amounts> amounts = bySellerAndMethod.get(seller.id());
                    return new SellerAmounts(seller, amounts.getOrDefault(PaymentMethod.CASH, Amounts.NONE),
                            amounts.values().stream().reduce(Amounts.NONE, Amounts::plus));
                })
                .toList();
        BigDecimal openingFloat = drawers.stream().map(CashDrawerResponse::openingFloat).reduce(ZERO, BigDecimal::add);
        return new CashClosingResponse(day, zone.getId(), sellerId, drawerId, closed, byPaymentMethod, total,
                bySeller, openingFloat, openingFloat.add(byPaymentMethod.get(PaymentMethod.CASH).net()), drawers);
    }

    private static void add(Map<Long, Ref> sellers, Map<Long, Map<PaymentMethod, Amounts>> bySellerAndMethod,
            SellerPaymentAmount row, Amounts amounts) {
        sellers.putIfAbsent(row.sellerId(), new Ref(row.sellerId(), row.sellerName()));
        bySellerAndMethod.computeIfAbsent(row.sellerId(), id -> new EnumMap<>(PaymentMethod.class))
                .merge(row.paymentMethod(), amounts, Amounts::plus);
    }

    private static ConflictException blindCount() {
        return new ConflictException("Close your cash drawer before viewing its cash closing");
    }
}
