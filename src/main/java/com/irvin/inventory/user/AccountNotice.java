package com.irvin.inventory.user;

// An email about the account itself (not a reset code), ready to send.
public record AccountNotice(String email, String subject, String body) {
}
