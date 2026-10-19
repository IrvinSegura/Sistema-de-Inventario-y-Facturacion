package com.irvin.inventory.common.exception;

// A feature that needs an external service which isn't configured (e.g. image storage) → 503.
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
