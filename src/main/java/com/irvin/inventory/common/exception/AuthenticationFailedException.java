package com.irvin.inventory.common.exception;

// Single generic message so responses never reveal which part of the credentials failed.
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Invalid credentials");
    }
}
