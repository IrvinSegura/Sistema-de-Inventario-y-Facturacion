package com.irvin.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.irvin.inventory.security.RateLimitProperties.Limit;
import org.junit.jupiter.api.Test;

class ClientRateLimiterTest {

    private static final String CLIENT = "198.51.100.7";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final ClientRateLimiter limiter = new ClientRateLimiter(new RateLimitProperties(
            new Limit(3, Duration.ofSeconds(10)), new Limit(1, Duration.ofMinutes(5)),
            new Limit(2, Duration.ofMinutes(1))), clock);

    @Test
    void allowsABurstThenOneRequestPerRefillPeriod() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, CLIENT)).isEmpty();
        }
        assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, CLIENT)).contains(Duration.ofSeconds(10));

        clock.advance(Duration.ofSeconds(4));
        assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, CLIENT)).contains(Duration.ofSeconds(6));

        clock.advance(Duration.ofSeconds(6));
        assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, CLIENT)).isEmpty();
        assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, CLIENT)).isPresent();
    }

    // Waiting longer than a full refill doesn't bank more than the capacity.
    @Test
    void refillStopsAtTheCapacity() {
        limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET, CLIENT);
        clock.advance(Duration.ofHours(1));

        assertThat(limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET, CLIENT)).isEmpty();
        assertThat(limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET, CLIENT)).isEmpty();
        assertThat(limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET, CLIENT)).isPresent();
    }

    @Test
    void policiesAndAddressesHaveSeparateBuckets() {
        assertThat(limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET_CODE, CLIENT)).isEmpty();
        assertThat(limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET_CODE, CLIENT)).isPresent();

        assertThat(limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET_CODE, "198.51.100.8")).isEmpty();
        assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, CLIENT)).isEmpty();
    }

    @Test
    void addressesInTheSameIpv6Slash64ShareABucket() {
        assertThat(limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET_CODE, "2001:db8:1:2::1")).isEmpty();
        assertThat(limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET_CODE, "2001:db8:1:2:ffff::9")).isPresent();
        assertThat(limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET_CODE, "2001:db8:1:3::1")).isEmpty();
    }

    @Test
    void clientKeyNormalizesIpv6ToItsPrefix() {
        assertThat(ClientRateLimiter.clientKey("2001:0db8:0001:0002:aaaa:bbbb:cccc:dddd"))
                .isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(ClientRateLimiter.clientKey("::ffff:198.51.100.7")).isEqualTo(CLIENT);
        assertThat(ClientRateLimiter.clientKey(CLIENT)).isEqualTo(CLIENT);
    }

    @Test
    void evictionDropsOnlyBucketsThatRefilled() {
        limiter.tryAcquire(RateLimitPolicy.LOGIN, CLIENT);
        limiter.tryAcquire(RateLimitPolicy.PASSWORD_RESET_CODE, CLIENT);

        clock.advance(Duration.ofSeconds(10));
        limiter.evictFullBuckets();
        assertThat(limiter.size()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(5));
        limiter.evictFullBuckets();
        assertThat(limiter.size()).isZero();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
