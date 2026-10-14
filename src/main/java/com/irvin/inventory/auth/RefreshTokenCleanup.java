package com.irvin.inventory.auth;

import java.time.Clock;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Each login/refresh inserts a row; expired ones are useless and would grow the table forever.
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanup {

    private static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(clock.instant().minus(RETENTION_AFTER_EXPIRY));
        log.info("Deleted {} expired refresh tokens", deleted);
    }
}
