package io.github.driftn2forty.chatsentry.util;

import java.util.concurrent.atomic.AtomicLong;

public final class RateLimiter {

    private final double maxTokens;
    private final double refillRate;
    private double tokens;
    private final AtomicLong lastRefillNanos = new AtomicLong(System.nanoTime());
    private final Object lock = new Object();

    public RateLimiter(double requestsPerSecond) {
        this.maxTokens = requestsPerSecond;
        this.refillRate = requestsPerSecond;
        this.tokens = requestsPerSecond;
    }

    public boolean tryAcquire() {
        synchronized (lock) {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    public double availableTokens() {
        synchronized (lock) {
            refill();
            return tokens;
        }
    }

    private void refill() {
        final long now = System.nanoTime();
        final long previous = lastRefillNanos.get();
        final double elapsedSeconds = (now - previous) / 1_000_000_000.0;
        if (elapsedSeconds > 0) {
            tokens = Math.min(maxTokens, tokens + elapsedSeconds * refillRate);
            lastRefillNanos.set(now);
        }
    }
}
