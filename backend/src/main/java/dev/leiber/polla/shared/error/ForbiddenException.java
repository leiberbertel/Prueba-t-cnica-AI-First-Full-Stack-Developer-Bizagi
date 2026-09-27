package dev.leiber.polla.shared.error;

/** El usuario está autenticado pero la operación no se le permite (p. ej. re-autenticación fallida). Se traduce a 403. */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
