package com.irvin.inventory.common.exception;

import java.time.Duration;

import lombok.Getter;

// A client sent too many requests; mapped to 429 with Retry-After.
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super("Too many requests, please retry later");
        this.retryAfter = retryAfter;
    }
}
