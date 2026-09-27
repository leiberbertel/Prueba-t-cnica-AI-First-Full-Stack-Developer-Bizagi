package dev.leiber.polla.shared.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties("app.security")
public record SecurityProperties(Jwt jwt, RefreshToken refreshToken, LoginRateLimit loginRateLimit, Cors cors) {

    /**
     * @param ephemeralSecretAllowed solo en el perfil local: sin secreto configurado se genera uno aleatorio por
     *                               arranque (las sesiones no sobreviven a un reinicio).
     */
    public record Jwt(String secret, String issuer, Duration accessTokenTtl, boolean ephemeralSecretAllowed) {
    }

    public record RefreshToken(Duration ttl, String cookieName, String cookiePath, boolean cookieSecure) {
    }

    public record LoginRateLimit(int maxAttempts, Duration window) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
