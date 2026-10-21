package com.irvin.inventory.stock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.irvin.inventory.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class InventoryMovementSearchTest {

    @Test
    void aStoreWideYearAcrossTheEndOfDaylightSavingIsAccepted() {
        // 2022-05-01 to 2023-05-01 in Mexico City (UTC-5 then UTC-6): 366 calendar days, 366 days and 1 h.
        assertThatCode(() -> new InventoryMovementSearch(null, MovementType.SALE,
                Instant.parse("2022-05-01T05:00:00Z"), Instant.parse("2023-05-02T06:00:00Z")))
                .doesNotThrowAnyException();
    }

    @Test
    void aStoreWideSearchPastThatHourIsRejected() {
        assertThatThrownBy(() -> new InventoryMovementSearch(null, MovementType.SALE,
                Instant.parse("2022-05-01T05:00:00Z"), Instant.parse("2023-05-02T06:00:01Z")))
                .isInstanceOf(BadRequestException.class);
    }
}
