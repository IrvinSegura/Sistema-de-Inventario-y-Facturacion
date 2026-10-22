package com.irvin.inventory.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.irvin.inventory.common.exception.BadRequestException;

// Accepted range for date filters. ISO parsing accepts years like +999999999, which overflow
// Java date arithmetic or PostgreSQL timestamps (500 instead of 400) if they reach a query.
public final class DateBounds {

    public static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    public static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 30);
    public static final Instant MIN_INSTANT = MIN_DATE.atStartOfDay(ZoneOffset.UTC).toInstant();
    public static final Instant MAX_INSTANT = MAX_DATE.atStartOfDay(ZoneOffset.UTC).toInstant();

    private DateBounds() {
    }

    // Throws 400 if any date is out of range; nulls (optional filters) are ignored.
    public static void requireWithin(LocalDate... dates) {
        for (LocalDate date : dates) {
            if (date != null && (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE))) {
                throw new BadRequestException("Dates must be between " + MIN_DATE + " and " + MAX_DATE);
            }
        }
    }

    // Throws 400 if an end is out of range or [from, to) is empty; nulls (optional filters) are ignored.
    // The names are the query parameters, for the message.
    public static void requireRange(Instant from, Instant to, String fromName, String toName) {
        requireWithin(from, to);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("'" + fromName + "' must be before '" + toName + "'");
        }
    }

    // Throws 400 if any instant is out of range; nulls (optional filters) are ignored.
    public static void requireWithin(Instant... instants) {
        for (Instant instant : instants) {
            if (instant != null && (instant.isBefore(MIN_INSTANT) || instant.isAfter(MAX_INSTANT))) {
                throw new BadRequestException("Dates must be between " + MIN_INSTANT + " and " + MAX_INSTANT);
            }
        }
    }
}
