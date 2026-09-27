package dev.leiber.polla.shared.error;

/**
 * Base de las excepciones de negocio. {@code code} identifica el problema de forma estable
 * y termina en el campo {@code type} del Problem Detail (RFC 9457).
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
