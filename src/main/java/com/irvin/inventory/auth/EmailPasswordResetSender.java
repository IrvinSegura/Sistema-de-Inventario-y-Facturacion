package com.irvin.inventory.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// Plain-text email over SMTP (spring.mail.*). Active only when an SMTP host is configured.
@Component
@ConditionalOnExpression("!'${spring.mail.host:}'.isBlank()")
@RequiredArgsConstructor
public class EmailPasswordResetSender implements PasswordResetSender {

    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    @Override
    public boolean supports(ResetChannel channel) {
        return channel == ResetChannel.EMAIL;
    }

    @Override
    public void send(ResetMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.mailFrom());
        mail.setTo(message.email());
        mail.setSubject(message.subject());
        mail.setText(message.emailBody());
        mailSender.send(mail);
    }
}
