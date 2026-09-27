package dev.leiber.polla.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Lectura tipada de los claims del access token del usuario autenticado. */
public final class CurrentUser {

    public static final String ROLES_CLAIM = "roles";
    public static final String NAME_CLAIM = "name";
    public static final String EMAIL_CLAIM = "email";

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
