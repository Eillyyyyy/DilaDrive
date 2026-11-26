package com.diladrive.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LoginThrottle {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int ACCOUNT_LIMIT = 5;
    private static final int IP_LIMIT = 30;
    private static final int MAX_BUCKETS = 20_000;

    private final Clock clock;
    private final Map<String, Attempts> attempts = new HashMap<>();
    private Instant nextCleanup = Instant.MIN;

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public synchronized Runnable begin(String email, String ip) {
        Instant now = clock.instant();
        if (!now.isBefore(nextCleanup)) {
            attempts.values().removeIf(bucket -> !now.isBefore(bucket.expires));
            nextCleanup = now.plusSeconds(60);
        }

        String normalizedEmail = email == null || email.length() > 254
                ? "" : email.strip().toLowerCase(Locale.ROOT);
        String accountKey = "email:" + normalizedEmail;
        String ipKey = "ip:" + ip;
        Attempts account = bucket(accountKey, now);
        Attempts address = bucket(ipKey, now);
        if (account == null || address == null || account.count >= ACCOUNT_LIMIT || address.count >= IP_LIMIT) {
            return null;
        }

        account.count++;
        address.count++;
        return () -> clearSuccessfulAttempt(account, address);
    }

    private synchronized void clearSuccessfulAttempt(Attempts account, Attempts address) {
        account.count--;
        address.count--;
    }

    private Attempts bucket(String key, Instant now) {
        Attempts existing = attempts.get(key);
        if (existing != null && now.isBefore(existing.expires)) return existing;
        if (existing == null && attempts.size() >= MAX_BUCKETS) return null;
        Attempts created = new Attempts(now.plus(WINDOW));
        attempts.put(key, created);
        return created;
    }

    private static final class Attempts {
        private final Instant expires;
        private int count;

        private Attempts(Instant expires) {
            this.expires = expires;
        }
    }
}
