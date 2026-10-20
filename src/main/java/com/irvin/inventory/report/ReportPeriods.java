package com.irvin.inventory.report;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.irvin.inventory.common.DateBounds;
import com.irvin.inventory.common.exception.BadRequestException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// What the period reports share: the periods they accept and the read-only snapshot their queries run in.
final class ReportPeriods {

    // A year (leap years included): bounds the daily breakdowns and the scanned rows.
    static final int MAX_DAYS = 366;
    // Seconds; JdbcTemplate turns the transaction timeout into a statement timeout.
    private static final int TIMEOUT_SECONDS = 10;

    private ReportPeriods() {
    }

    // Inclusive calendar days; checked before the transaction starts, so invalid requests never take
    // a pool connection.
    static void requireValid(LocalDate from, LocalDate to) {
        DateBounds.requireWithin(from, to);
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BadRequestException("The period can't exceed " + MAX_DAYS + " days");
        }
    }

    // One snapshot for every query, so totals and breakdowns agree even while documents come in.
    // The timeout keeps a heavy report from holding a pool connection the checkout needs.
    static TransactionTemplate snapshot(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        template.setTimeout(TIMEOUT_SECONDS);
        return template;
    }
}
