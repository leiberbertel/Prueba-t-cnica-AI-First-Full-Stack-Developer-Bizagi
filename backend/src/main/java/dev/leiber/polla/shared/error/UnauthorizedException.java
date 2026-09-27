package dev.leiber.polla.shared.error;

public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super("unauthorized", message);
    }
}
