package com.irvin.inventory.auth;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// Keeps codes instead of delivering them, for every channel, so tests can read them. Codes are
// dispatched in the background, hence the waits.
public class RecordingPasswordResetSender implements PasswordResetSender {

    private final Map<String, BlockingQueue<ResetMessage>> byEmail = new ConcurrentHashMap<>();

    @Override
    public boolean supports(ResetChannel channel) {
        return true;
    }

    @Override
    public void send(ResetMessage message) {
        queue(message.email()).add(message);
    }

    public ResetMessage next(String email) throws InterruptedException {
        ResetMessage message = queue(email).poll(5, TimeUnit.SECONDS);
        if (message == null) {
            throw new AssertionError("No password reset code was sent to " + email);
        }
        return message;
    }

    public boolean nothingSentTo(String email) throws InterruptedException {
        return queue(email).poll(500, TimeUnit.MILLISECONDS) == null;
    }

    private BlockingQueue<ResetMessage> queue(String email) {
        return byEmail.computeIfAbsent(email, key -> new LinkedBlockingQueue<>());
    }
}
