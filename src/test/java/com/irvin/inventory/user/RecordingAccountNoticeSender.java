package com.irvin.inventory.user;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// Keeps account notices instead of emailing them, so tests can read them. They're sent in the
// background after the change commits, hence the waits.
public class RecordingAccountNoticeSender implements AccountNoticeSender {

    private final Map<String, BlockingQueue<AccountNotice>> byEmail = new ConcurrentHashMap<>();

    @Override
    public void send(AccountNotice notice) {
        queue(notice.email()).add(notice);
    }

    public AccountNotice next(String email) throws InterruptedException {
        AccountNotice notice = queue(email).poll(5, TimeUnit.SECONDS);
        if (notice == null) {
            throw new AssertionError("No account notice was sent to " + email);
        }
        return notice;
    }

    public boolean nothingSentTo(String email) throws InterruptedException {
        return queue(email).poll(500, TimeUnit.MILLISECONDS) == null;
    }

    private BlockingQueue<AccountNotice> queue(String email) {
        return byEmail.computeIfAbsent(email, key -> new LinkedBlockingQueue<>());
    }
}
