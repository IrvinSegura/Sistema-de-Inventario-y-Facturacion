package com.irvin.inventory.user;

import com.irvin.inventory.auth.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// Plain-text email over the same SMTP server and sender as the reset codes (EmailPasswordResetSender).
@Component
@ConditionalOnExpression("!'${spring.mail.host:}'.isBlank()")
@RequiredArgsConstructor
public class EmailAccountNoticeSender implements AccountNoticeSender {

    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    @Override
    public void send(AccountNotice notice) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.mailFrom());
        mail.setTo(notice.email());
        mail.setSubject(notice.subject());
        mail.setText(notice.body());
        mailSender.send(mail);
    }
}
