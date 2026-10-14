package com.irvin.inventory.config;

import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Sales are stored in UTC; reports group them by calendar day in the store's time zone.
@Validated
@ConfigurationProperties("app.store")
public record StoreProperties(@NotNull ZoneId timeZone) {

    public StoreProperties {
        // PostgreSQL reads offsets like "-06:00" with the POSIX convention (inverted sign),
        // so only region IDs such as America/Mexico_City (or UTC) mean the same in Java and SQL.
        if (timeZone != null && timeZone.getId().matches(".*[+-]\\d.*")) {
            throw new IllegalArgumentException(
                    "app.store.time-zone must be a region ID such as America/Mexico_City, not an offset");
        }
    }
}
