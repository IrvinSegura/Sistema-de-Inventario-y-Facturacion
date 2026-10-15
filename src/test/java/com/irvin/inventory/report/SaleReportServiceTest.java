package com.irvin.inventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.config.StoreProperties;
import com.irvin.inventory.report.SaleReportRepository.PaymentMethodAmount;
import com.irvin.inventory.report.dto.CashClosingResponse;
import com.irvin.inventory.report.dto.CashClosingResponse.Amounts;
import com.irvin.inventory.report.dto.SaleReportResponse;
import com.irvin.inventory.report.dto.SaleReportResponse.DailyTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.Totals;
import com.irvin.inventory.sale.PaymentMethod;
import com.irvin.inventory.sale.SaleStatus;
import com.irvin.inventory.security.CurrentUser;
import com.irvin.inventory.user.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

class SaleReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    private static final CurrentUser ADMIN = new CurrentUser(1L, Role.ADMIN);
    private static final CurrentUser SELLER = new CurrentUser(7L, Role.SELLER);
    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    // 03:00 UTC on Mar 11 is still Mar 10 in Mexico City (UTC-6).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-11T03:00:00Z"), ZoneOffset.UTC);

    private final SaleReportRepository repository = mock(SaleReportRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SaleReportService service =
            new SaleReportService(repository, new StoreProperties(ZONE), CLOCK, transactionManager);

    @Test
    void sellerIsAlwaysScopedToTheirOwnSales() {
        service.salesReport(DAY, DAY, 99L, SELLER);

        assertThat(capturedPeriod().sellerId()).isEqualTo(SELLER.id());
    }

    @Test
    void adminKeepsTheRequestedSellerFilterOrNone() {
        service.salesReport(DAY, DAY, 99L, ADMIN);
        assertThat(capturedPeriod().sellerId()).isEqualTo(99L);
    }

    @Test
    void periodCoversWholeLocalDaysWithAnExclusiveEnd() {
        service.salesReport(DAY, DAY.plusDays(1), null, ADMIN);

        SaleReportRepository.Period period = capturedPeriod();
        assertThat(period.from()).isEqualTo(DAY);
        assertThat(period.toExclusive()).isEqualTo(DAY.plusDays(2));
        assertThat(period.zone()).isEqualTo(ZONE);
        assertThat(period.sellerId()).isNull();
    }

    @Test
    void averageTicketRoundsHalfUpAndDaysWithoutSalesAreFilledWithZeros() {
        when(repository.totalsByStatus(any())).thenReturn(Map.of(SaleStatus.COMPLETED,
                new Totals(3, new BigDecimal("10.00"), new BigDecimal("0.00"), new BigDecimal("10.00"))));
        when(repository.completedByDay(any())).thenReturn(Map.of(DAY.plusDays(1),
                new DailyTotal(DAY.plusDays(1), 3, new BigDecimal("10.00"))));

        SaleReportResponse report = service.salesReport(DAY, DAY.plusDays(2), null, ADMIN);

        // 10.00 / 3 = 3.333...
        assertThat(report.averageTicket()).isEqualTo(new BigDecimal("3.33"));
        assertThat(report.cancelled().saleCount()).isZero();
        assertThat(report.byDay()).extracting(DailyTotal::saleCount).containsExactly(0L, 3L, 0L);
        assertThat(report.byDay().getFirst().total()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void averageTicketRoundsHalfUpAtTheMidpoint() {
        when(repository.totalsByStatus(any())).thenReturn(Map.of(SaleStatus.COMPLETED,
                new Totals(2, new BigDecimal("0.05"), new BigDecimal("0.00"), new BigDecimal("0.05"))));

        // 0.05 / 2 = 0.025 -> 0.03 (HALF_EVEN would give 0.02).
        assertThat(service.salesReport(DAY, DAY, null, ADMIN).averageTicket()).isEqualTo(new BigDecimal("0.03"));
    }

    @Test
    void invalidPeriodsFailBeforeTouchingTheDatabase() {
        List<LocalDate[]> invalid = List.of(
                new LocalDate[] {DAY, DAY.minusDays(1)},
                new LocalDate[] {LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)},
                new LocalDate[] {LocalDate.of(1999, 12, 31), LocalDate.of(2000, 1, 1)},
                new LocalDate[] {LocalDate.of(9999, 12, 31), LocalDate.of(9999, 12, 31)});
        for (LocalDate[] range : invalid) {
            assertThatThrownBy(() -> service.salesReport(range[0], range[1], null, ADMIN))
                    .isInstanceOf(BadRequestException.class);
        }

        verifyNoInteractions(repository, transactionManager);
    }

    @Test
    void periodOf366DaysIsAccepted() {
        // 2024 is a leap year.
        service.salesReport(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null, ADMIN);

        verify(repository).totalsByStatus(any());
    }

    @Test
    void cashClosingDefaultsToTodayAndScopesBothQueriesToTheSeller() {
        service.cashClosing(null, 99L, SELLER);

        ArgumentCaptor<SaleReportRepository.Period> sales = ArgumentCaptor.forClass(SaleReportRepository.Period.class);
        ArgumentCaptor<SaleReportRepository.Period> cancellations =
                ArgumentCaptor.forClass(SaleReportRepository.Period.class);
        verify(repository).salesByPaymentMethod(sales.capture());
        verify(repository).cancellationsByPaymentMethod(cancellations.capture());
        assertThat(sales.getValue().from()).isEqualTo(DAY);
        assertThat(sales.getValue().toExclusive()).isEqualTo(DAY.plusDays(1));
        assertThat(sales.getValue().sellerId()).isEqualTo(SELLER.id());
        assertThat(cancellations.getValue()).isEqualTo(sales.getValue());
    }

    @Test
    void cashClosingNetsCancellationsPerPaymentMethod() {
        when(repository.salesByPaymentMethod(any())).thenReturn(List.of(
                new PaymentMethodAmount(PaymentMethod.CARD, 1, new BigDecimal("20.00")),
                new PaymentMethodAmount(PaymentMethod.CASH, 3, new BigDecimal("30.00"))));
        when(repository.cancellationsByPaymentMethod(any())).thenReturn(List.of(
                new PaymentMethodAmount(PaymentMethod.CASH, 2, new BigDecimal("12.50"))));

        CashClosingResponse closing = service.cashClosing(DAY, null, ADMIN);

        assertThat(closing.byPaymentMethod().keySet())
                .containsExactly(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.TRANSFER);
        assertThat(closing.byPaymentMethod().get(PaymentMethod.CASH).net()).isEqualTo(new BigDecimal("17.50"));
        assertThat(closing.byPaymentMethod().get(PaymentMethod.TRANSFER)).isEqualTo(Amounts.NONE);
        assertThat(closing.expectedCash()).isEqualTo(new BigDecimal("17.50"));
        assertThat(closing.total()).isEqualTo(new Amounts(4, new BigDecimal("50.00"), 2,
                new BigDecimal("12.50"), new BigDecimal("37.50")));
    }

    @Test
    void cashClosingIsClosedOnlyAfterTheDayEndsPlusTheMargin() {
        // Mar 10 ends at 2026-03-11T06:00Z in Mexico City; the margin is 60 s.
        assertThat(serviceAt("2026-03-11T06:00:59Z").cashClosing(DAY, null, ADMIN).closed()).isFalse();
        assertThat(serviceAt("2026-03-11T06:01:00Z").cashClosing(DAY, null, ADMIN).closed()).isTrue();
        // The default clock is still on Mar 10.
        assertThat(service.cashClosing(DAY, null, ADMIN).closed()).isFalse();
        assertThat(service.cashClosing(DAY.minusDays(1), null, ADMIN).closed()).isTrue();
    }

    @Test
    void cashClosingRejectsDatesOutOfBounds() {
        assertThatThrownBy(() -> service.cashClosing(LocalDate.of(1999, 12, 31), null, ADMIN))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository, transactionManager);
    }

    private SaleReportService serviceAt(String instant) {
        return new SaleReportService(repository, new StoreProperties(ZONE),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), transactionManager);
    }

    @Test
    void fixedOffsetTimeZonesAreRejected() {
        // PostgreSQL inverts the sign of POSIX offsets, so "-06:00" would group days 12 hours off.
        for (String offset : List.of("-06:00", "UTC-06:00", "Etc/GMT+6")) {
            assertThatThrownBy(() -> new StoreProperties(ZoneId.of(offset)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new StoreProperties(ZoneId.of("UTC")).timeZone().getId()).isEqualTo("UTC");
    }

    private SaleReportRepository.Period capturedPeriod() {
        ArgumentCaptor<SaleReportRepository.Period> period = ArgumentCaptor.forClass(SaleReportRepository.Period.class);
        verify(repository).totalsByStatus(period.capture());
        return period.getValue();
    }
}
