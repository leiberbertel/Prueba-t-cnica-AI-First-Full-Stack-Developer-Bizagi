package dev.leiber.polla.auth.application;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.leiber.polla.MutableClock;
import dev.leiber.polla.shared.error.TooManyRequestsException;
import dev.leiber.polla.shared.security.SecurityProperties;

class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T12:00:00Z"));
    private final LoginRateLimiter limiter = new LoginRateLimiter(
            new SecurityProperties(null, null, new SecurityProperties.LoginRateLimit(3, Duration.ofMinutes(1)), null),
            clock);

    @Test
    void blocksAfterMaxAttemptsWithinWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("10.0.0.1");
        }

        assertThatThrownBy(() -> limiter.acquire("10.0.0.1")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void countsEachKeySeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("10.0.0.1");
        }

        assertThatNoException().isThrownBy(() -> limiter.acquire("10.0.0.2"));
    }

    @Test
    void resetsWhenWindowEnds() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("10.0.0.1");
        }
        clock.set(clock.instant().plus(Duration.ofMinutes(1)));

        assertThatNoException().isThrownBy(() -> limiter.acquire("10.0.0.1"));
    }
}
