package dev.leiber.polla.auth.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leiber.polla.shared.error.UnauthorizedException;
import dev.leiber.polla.shared.security.CurrentUser;
import dev.leiber.polla.shared.security.SecurityProperties;

/**
 * Emite access tokens JWT y administra refresh tokens opacos con rotación y detección de reutilización (ADR-0004).
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final int REFRESH_TOKEN_BYTES = 32;
    /** Refresh concurrentes legítimos (p. ej. dos pestañas recargando a la vez) no se tratan como robo. */
    private static final Duration REUSE_GRACE_PERIOD = Duration.ofSeconds(10);

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens, SecurityProperties properties,
            Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issueAccessToken(UserAccount user) {
        var now = clock.instant();
        var ttl = properties.jwt().accessTokenTtl();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(CurrentUser.EMAIL_CLAIM, user.getEmail())
                .claim(CurrentUser.NAME_CLAIM, user.getDisplayName())
                .claim(CurrentUser.ROLES_CLAIM, List.of(user.getRole().name()))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, ttl.toSeconds());
    }

    @Transactional
    public String issueRefreshToken(UserAccount user) {
        var raw = randomToken();
        var now = clock.instant();
        refreshTokens.save(new RefreshToken(user, hash(raw), now, now.plus(properties.refreshToken().ttl())));
        return raw;
    }

    /** Valida el refresh token, lo revoca y emite uno nuevo. Devuelve el usuario dueño y el nuevo token. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        var now = clock.instant();
        var token = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(TokenService::invalidSession);

        if (token.isRevoked()) {
            boolean withinGrace = token.getRevokedAt().plus(REUSE_GRACE_PERIOD).isAfter(now);
            if (!withinGrace) {
                // Un token ya rotado se volvió a usar: posible robo. Se cierran todas las sesiones del usuario.
                log.warn("Refresh token reuse detected for user {}. Revoking all sessions.", token.getUser().getId());
                refreshTokens.revokeAllForUser(token.getUser().getId(), now);
            }
            throw invalidSession();
        }
        if (token.isExpired(now)) {
            throw invalidSession();
        }

        token.revoke(now);
        var user = token.getUser();
        return new Rotation(user, issueRefreshToken(user));
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(clock.instant()));
    }

    private String randomToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static UnauthorizedException invalidSession() {
        return new UnauthorizedException("La sesión expiró. Inicia sesión de nuevo.");
    }

    public record AccessToken(String value, long expiresInSeconds) {
    }

    public record Rotation(UserAccount user, String refreshToken) {
    }
}
