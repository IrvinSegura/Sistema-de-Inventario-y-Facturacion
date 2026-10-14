package com.irvin.inventory.common.exception;

// Business-level permission rule (e.g. a field only admins may change); role checks use @PreAuthorize.
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
