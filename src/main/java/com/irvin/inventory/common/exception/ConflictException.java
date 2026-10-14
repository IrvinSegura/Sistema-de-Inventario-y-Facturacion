package com.irvin.inventory.common.exception;

public class ConflictException extends RuntimeException {

    public static final String STALE_VERSION_MESSAGE =
            "The resource was modified by another user; reload it and try again";

    public ConflictException(String message) {
        super(message);
    }

    public static ConflictException staleVersion() {
        return new ConflictException(STALE_VERSION_MESSAGE);
    }
}
