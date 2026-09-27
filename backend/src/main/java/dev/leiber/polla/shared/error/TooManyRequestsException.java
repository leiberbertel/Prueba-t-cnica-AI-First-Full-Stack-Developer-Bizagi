package dev.leiber.polla.shared.error;

import java.time.Duration;

public class TooManyRequestsException extends DomainException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super("too-many-requests", message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
