package com.irvin.inventory.user.dto;

import java.time.Instant;

import com.irvin.inventory.user.AccountEvent;
import com.irvin.inventory.user.AccountEventType;

public record AccountEventResponse(
        Long id,
        AccountEventType type,
        // Only for EMAIL_CHANGED (emails) and ROLE_CHANGED (roles).
        String oldValue,
        String newValue,
        Actor actor,
        Instant occurredAt) {

    public record Actor(Long id, String name) {
    }

    public static AccountEventResponse from(AccountEvent event) {
        return new AccountEventResponse(event.getId(), event.getType(), event.getOldValue(), event.getNewValue(),
                new Actor(event.getActor().getId(), event.getActor().getName()), event.getOccurredAt());
    }
}
