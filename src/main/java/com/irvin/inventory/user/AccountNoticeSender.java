package com.irvin.inventory.user;

// Delivers account notices; none is configured without an SMTP host, and then they're only logged.
public interface AccountNoticeSender {

    void send(AccountNotice notice);
}
