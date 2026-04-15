package io.github.driftn2forty.chatsentry.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void acquireConsumesToken() {
        final RateLimiter limiter = new RateLimiter(5.0);
        assertTrue(limiter.tryAcquire());
        assertEquals(4.0, limiter.availableTokens(), 0.5);
    }

    @Test
    void exhaustBurstCapacity() {
        final RateLimiter limiter = new RateLimiter(3.0);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void initialTokensEqualCapacity() {
        final RateLimiter limiter = new RateLimiter(10.0);
        assertEquals(10.0, limiter.availableTokens(), 0.1);
    }

    @Test
    void tokensDontExceedCapacity() {
        final RateLimiter limiter = new RateLimiter(2.0);
        final double available = limiter.availableTokens();
        assertTrue(available <= 2.0);
    }

    @Test
    void singleTokenCapacity() {
        final RateLimiter limiter = new RateLimiter(1.0);
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void fractionalCapacityNeverAcquires() {
        final RateLimiter limiter = new RateLimiter(0.5);
        assertFalse(limiter.tryAcquire());
    }
}
