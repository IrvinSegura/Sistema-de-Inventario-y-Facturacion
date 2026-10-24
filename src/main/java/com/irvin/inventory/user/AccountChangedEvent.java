package com.irvin.inventory.user;

// Published by an admin's change that the account's owner must hear about; AccountChangeNotifier
// emails `recipient` after the change commits.
public record AccountChangedEvent(Kind kind, String name, String recipient, String newValue) {

    public enum Kind {
        // To the old address; newValue is the new email.
        EMAIL_CHANGED,
        // To the account's email; newValue is the new phone, or null when it was removed.
        PHONE_CHANGED,
        // To the account's email; no newValue.
        PASSWORD_RESET
    }
}
