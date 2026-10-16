package com.irvin.inventory.auth;

// Delivers reset codes through one or more channels (email provider, SMS provider...).
public interface PasswordResetSender {

    boolean supports(ResetChannel channel);

    void send(ResetMessage message);
}
