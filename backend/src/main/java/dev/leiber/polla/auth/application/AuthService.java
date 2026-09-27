package dev.leiber.polla.auth.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leiber.polla.auth.AccountDirectory;
import dev.leiber.polla.auth.UserAccountDeleted;
import dev.leiber.polla.auth.domain.Role;
import dev.leiber.polla.auth.domain.UserAccount;
import dev.leiber.polla.auth.infrastructure.UserAccountRepository;
import dev.leiber.polla.shared.error.ConflictException;
import dev.leiber.polla.shared.error.ForbiddenException;
import dev.leiber.polla.shared.error.InvalidRequestException;
import dev.leiber.polla.shared.error.NotFoundException;
import dev.leiber.polla.shared.error.UnauthorizedException;

/** Casos de uso de autenticación (specs/01-auth). */
@Service
public class AuthService implements AccountDirectory {

    /** BCrypt ignora lo que pase de 72 bytes: se rechaza en vez de truncar en silencio. */
    static final int BCRYPT_MAX_BYTES = 72;

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** Hash de referencia para igualar el tiempo de respuesta cuando el email no existe (evita enumeración). */
    private final String dummyHash;

    AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-password");
    }

    @Transactional
    public AuthResult register(String email, String displayName, String password) {
        var normalizedEmail = normalizeEmail(email);
        ensurePasswordFitsBcrypt(password);
        if (users.existsByEmail(normalizedEmail)) {
            throw new ConflictException("email-taken", "Ya existe una cuenta con ese email.");
        }
        var user = users.save(new UserAccount(normalizedEmail, displayName.trim(), passwordEncoder.encode(password),
                Role.USER, clock.instant()));
        return authenticated(user);
    }

    @Transactional
    public AuthResult login(String email, String password) {
        var user = users.findByEmail(normalizeEmail(email)).orElse(null);
        boolean matches = passwordMatches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !matches) {
            throw new UnauthorizedException("Email o contraseña incorrectos.");
        }
        return authenticated(user);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("La sesión expiró. Inicia sesión de nuevo.");
        }
        var rotation = tokens.rotate(refreshToken);
        return new AuthResult(tokens.issueAccessToken(rotation.user()), rotation.user(), rotation.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokens.revoke(refreshToken);
        }
    }

    /**
     * Fase síncrona de la eliminación de cuenta (HU-01.6, ADR-0007): transacción corta que marca la cuenta, anonimiza
     * los datos personales, revoca las sesiones y publica {@link UserAccountDeleted}. Los datos se purgan después,
     * por lotes, en cada módulo. Exige la contraseña actual y protege la cuenta admin (RN-10).
     */
    @Transactional(timeout = 5)
    public void deleteAccount(long userId, String password) {
        var user = getUser(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new ConflictException("admin-cannot-be-deleted",
                    "La cuenta administradora no se puede eliminar: la polla quedaría sin quien registre resultados.");
        }
        if (!passwordMatches(password, user.getPasswordHash())) {
            throw new ForbiddenException("invalid-password", "La contraseña no es correcta.");
        }
        user.markDeleted(clock.instant());
        tokens.revokeAllForUser(userId);
        events.publishEvent(new UserAccountDeleted(userId));
    }

    @Transactional(readOnly = true)
    public UserAccount getUser(long userId) {
        return users.findByIdAndDeletedAtIsNull(userId).orElseThrow(() -> new NotFoundException("Usuario", userId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(long userId) {
        return users.existsByIdAndDeletedAtIsNull(userId);
    }

    private AuthResult authenticated(UserAccount user) {
        return new AuthResult(tokens.issueAccessToken(user), user, tokens.issueRefreshToken(user));
    }

    private boolean passwordMatches(String password, String hash) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
                && passwordEncoder.matches(password, hash);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void ensurePasswordFitsBcrypt(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new InvalidRequestException("La contraseña es demasiado larga.");
        }
    }

    public record AuthResult(TokenService.AccessToken accessToken, UserAccount user, String refreshToken) {
    }
}
