package com.irvin.inventory.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

// SMS through Twilio's Messages REST API (no SDK needed). Active only when an account is configured.
@Component
@ConditionalOnExpression("!'${app.sms.twilio.account-sid:}'.isBlank()")
public class TwilioSmsSender implements PasswordResetSender {

    private static final String API_URL = "https://api.twilio.com/2010-04-01";

    private final TwilioProperties properties;
    private final RestClient restClient;

    public TwilioSmsSender(TwilioProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(API_URL)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.accountSid(), properties.authToken()))
                .build();
    }

    @Override
    public boolean supports(ResetChannel channel) {
        return channel == ResetChannel.SMS;
    }

    @Override
    public void send(ResetMessage message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", message.phone());
        form.add("From", properties.fromNumber());
        form.add("Body", message.smsBody());
        restClient.post()
                .uri("/Accounts/{sid}/Messages.json", properties.accountSid())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
