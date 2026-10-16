package com.irvin.inventory.security;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// In-memory token buckets per policy and client address. Enough for a single instance: buckets
// are lost on restart and not shared between instances (a second instance needs a shared store).
@Component
public class ClientRateLimiter {

    private static final int IPV6_PREFIX_BYTES = 8;

    private final Map<Key, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;
    private final Clock clock;

    public ClientRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    // Empty when the request may proceed; otherwise how long until the next one is allowed.
    public Optional<Duration> tryAcquire(RateLimitPolicy policy, String clientAddress) {
        RateLimitProperties.Limit limit = properties.limitFor(policy);
        Instant now = clock.instant();
        Duration[] wait = new Duration[1];
        // compute is atomic per key, so concurrent requests from one client can't overspend.
        buckets.compute(new Key(policy, clientKey(clientAddress)), (key, bucket) -> {
            long cost = limit.refillPeriod().toNanos();
            long credit = bucket == null ? maxCredit(limit) : bucket.creditAt(now, limit);
            if (credit >= cost) {
                return new Bucket(credit - cost, now);
            }
            wait[0] = Duration.ofNanos(cost - credit);
            return new Bucket(credit, now);
        });
        return Optional.ofNullable(wait[0]);
    }

    // A full bucket is the same as no bucket: dropping them bounds memory to recently active clients.
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void evictFullBuckets() {
        Instant now = clock.instant();
        buckets.entrySet().removeIf(entry -> {
            RateLimitProperties.Limit limit = properties.limitFor(entry.getKey().policy());
            return entry.getValue().creditAt(now, limit) >= maxCredit(limit);
        });
    }

    private static long maxCredit(RateLimitProperties.Limit limit) {
        return Math.multiplyExact(limit.refillPeriod().toNanos(), limit.capacity());
    }

    int size() {
        return buckets.size();
    }

    // An IPv6 client usually controls a whole /64, so it counts as one client.
    static String clientKey(String address) {
        if (address == null || address.indexOf(':') < 0) {
            return address;
        }
        try {
            // A literal with ':' is parsed, never resolved through DNS.
            InetAddress parsed = InetAddress.getByName(address);
            if (!(parsed instanceof Inet6Address)) {
                return parsed.getHostAddress(); // IPv4-mapped
            }
            byte[] prefix = Arrays.copyOf(parsed.getAddress(), 16);
            Arrays.fill(prefix, IPV6_PREFIX_BYTES, prefix.length, (byte) 0);
            return InetAddress.getByAddress(prefix).getHostAddress() + "/64";
        } catch (UnknownHostException ex) {
            return address;
        }
    }

    private record Key(RateLimitPolicy policy, String client) {
    }

    // Credit is banked time in nanoseconds (exact, no floating point): a request costs one refill
    // period, and at most `capacity` periods are banked.
    private record Bucket(long credit, Instant updatedAt) {

        long creditAt(Instant now, RateLimitProperties.Limit limit) {
            Duration elapsed = Duration.between(updatedAt, now);
            if (elapsed.isNegative()) {
                return credit; // the clock went back
            }
            long missing = maxCredit(limit) - credit;
            return elapsed.compareTo(Duration.ofNanos(missing)) >= 0 ? maxCredit(limit) : credit + elapsed.toNanos();
        }
    }
}
