package com.irvin.inventory.common;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Serializes requests that share an Idempotency-Key, so the "already processed?" check and the
// insert can't interleave. Released when the transaction ends; take it before any row lock.
@Component
@RequiredArgsConstructor
public class IdempotencyLock {

    private final JdbcTemplate jdbcTemplate;

    // The scope (e.g. "purchase") keeps keys of different documents from sharing a lock.
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String scope, UUID key) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, scope + ":" + key);
    }
}
