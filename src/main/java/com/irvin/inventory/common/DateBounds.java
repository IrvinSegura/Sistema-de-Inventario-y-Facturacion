package com.irvin.inventory.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

// Accepted range for date filters. ISO parsing accepts years like +999999999, which overflow
// Java date arithmetic or PostgreSQL timestamps (500 instead of 400) if they reach a query.
public final class DateBounds {

    public static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    public static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 30);
    public static final Instant MIN_INSTANT = MIN_DATE.atStartOfDay(ZoneOffset.UTC).toInstant();
    public static final Instant MAX_INSTANT = MAX_DATE.atStartOfDay(ZoneOffset.UTC).toInstant();

    private DateBounds() {
    }

    public static boolean isOutside(Instant instant) {
        return instant != null && (instant.isBefore(MIN_INSTANT) || instant.isAfter(MAX_INSTANT));
    }
}
