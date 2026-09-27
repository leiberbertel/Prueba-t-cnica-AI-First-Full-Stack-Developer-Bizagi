package dev.leiber.polla.auth;

/** API pública del módulo para consultar el estado de una cuenta. */
public interface AccountDirectory {

    /** {@code false} si la cuenta no existe o fue eliminada (aunque sus datos aún se estén purgando). */
    boolean isActive(long userId);
}
