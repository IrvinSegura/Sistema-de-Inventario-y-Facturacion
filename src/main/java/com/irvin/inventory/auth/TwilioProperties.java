package com.irvin.inventory.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Twilio account used to send SMS; fromNumber is a Twilio number in E.164 (+15005550006).
@ConfigurationProperties("app.sms.twilio")
public record TwilioProperties(String accountSid, String authToken, String fromNumber) {
}
