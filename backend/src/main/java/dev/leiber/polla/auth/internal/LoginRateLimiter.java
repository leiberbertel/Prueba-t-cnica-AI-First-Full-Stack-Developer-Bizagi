package dev.leiber.polla.auth.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.leiber.polla.shared.error.TooManyRequestsException;
import dev.leiber.polla.shared.security.SecurityProperties;

/**
 * Limita intentos de login por clave (IP) con ventana fija (CA-01.10).
 * <p>
 * En memoria: suficiente para una instancia. Con varias instancias se movería a Redis/Bucket4j (mismo contrato).
 */
@Component
public class LoginRateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration windowSize;
    private final Clock clock;

    LoginRateLimiter(SecurityProperties properties, Clock clock) {
        this.maxAttempts = properties.loginRateLimit().maxAttempts();
        this.windowSize = properties.loginRateLimit().window();
        this.clock = clock;
    }

    public void acquire(String key) {
        var now = clock.instant();
        var window = windows.compute(key, (k, current) ->
                current == null || current.isOver(now) ? new Window(now.plus(windowSize), 1) : current.increment());
        if (window.attempts() > maxAttempts) {
            throw new TooManyRequestsException("Demasiados intentos. Espera un momento e intenta de nuevo.",
                    Duration.between(now, window.resetsAt()));
        }
    }

    @Scheduled(fixedDelay = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void evictExpired() {
        var now = clock.instant();
        windows.values().removeIf(window -> window.isOver(now));
    }

    private record Window(Instant resetsAt, int attempts) {

        boolean isOver(Instant now) {
            return !now.isBefore(resetsAt);
        }

        Window increment() {
            return new Window(resetsAt, attempts + 1);
        }
    }
}
