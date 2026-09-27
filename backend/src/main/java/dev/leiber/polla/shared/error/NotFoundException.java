package dev.leiber.polla.shared.error;

public class NotFoundException extends DomainException {

    public NotFoundException(String resource, Object id) {
        super("not-found", "%s %s no existe".formatted(resource, id));
    }
}
