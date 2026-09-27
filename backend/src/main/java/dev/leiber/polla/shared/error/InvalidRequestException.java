package dev.leiber.polla.shared.error;

/** Petición inválida por una regla que no se puede expresar con Jakarta Validation. Se traduce a 400. */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String message) {
        super("validation", message);
    }
}
