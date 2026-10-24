package com.irvin.inventory.user;

// Values allowed by ck_account_event_type (V30). A new value needs a migration replacing that check, and
// its text in the web's users.history.types.
public enum AccountEventType {
    CREATED,
    EMAIL_CHANGED,
    PHONE_CHANGED,
    ROLE_CHANGED,
    DEACTIVATED,
    REACTIVATED,
    // By an admin, to a temporary password.
    PASSWORD_RESET,
    // By the user, knowing the current one; old_value is TEMPORARY when it replaced an admin's temporary one.
    PASSWORD_CHANGED,
    // By the user, with a forgotten-password code.
    PASSWORD_RECOVERED
}
