package com.irvin.inventory.auth;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Sends codes in the background: the request answers at once whether or not the account exists
// (and a slow provider doesn't hold the request). Failures are logged, never shown to the client.
@Slf4j
@Component
public class PasswordResetDispatcher {

    private final List<PasswordResetSender> senders;
    private final PasswordResetProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PasswordResetDispatcher(List<PasswordResetSender> senders, PasswordResetProperties properties) {
        this.senders = senders;
        this.properties = properties;
    }

    public void dispatch(ResetMessage message) {
        executor.execute(() -> deliver(message));
    }

    private void deliver(ResetMessage message) {
        PasswordResetSender sender = senders.stream()
                .filter(candidate -> candidate.supports(message.channel()))
                .findFirst()
                .orElse(null);
        if (sender == null) {
            logUnsent(message);
            return;
        }
        try {
            sender.send(message);
        } catch (RuntimeException e) {
            log.error("Could not send the {} password reset code of user {}", message.channel(), message.userId(), e);
        }
    }

    private void logUnsent(ResetMessage message) {
        if (properties.logCodes()) {
            log.warn("No {} provider configured; password reset code of user {}: {}",
                    message.channel(), message.userId(), message.code());
        } else {
            log.warn("No {} provider configured; password reset code of user {} was not sent",
                    message.channel(), message.userId());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.close();
    }
}
