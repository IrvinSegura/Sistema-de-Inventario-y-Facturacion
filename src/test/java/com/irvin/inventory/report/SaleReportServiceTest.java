package com.irvin.inventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.config.StoreProperties;
import com.irvin.inventory.report.dto.SaleReportResponse;
import com.irvin.inventory.report.dto.SaleReportResponse.DailyTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.Totals;
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

    private final SaleReportRepository repository = mock(SaleReportRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SaleReportService service =
            new SaleReportService(repository, new StoreProperties(ZONE), transactionManager);

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
