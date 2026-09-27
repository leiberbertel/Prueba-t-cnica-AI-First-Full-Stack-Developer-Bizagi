package dev.leiber.polla.auth.infrastructure;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.leiber.polla.auth.application.AuthService;
import dev.leiber.polla.auth.domain.Role;
import dev.leiber.polla.auth.domain.UserAccount;

/**
 * Crea la cuenta admin al arrancar si no existe (CA-01.11). Nadie puede registrarse como admin (RN-08).
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties admin;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    AdminBootstrap(AdminProperties admin, UserAccountRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.admin = admin;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!admin.isConfigured()) {
            log.warn("ADMIN_EMAIL/ADMIN_PASSWORD no configurados: no se crea la cuenta admin.");
            return;
        }
        var email = AuthService.normalizeEmail(admin.email());
        if (users.existsByEmail(email)) {
            return;
        }
        users.save(new UserAccount(email, admin.displayName(), passwordEncoder.encode(admin.password()), Role.ADMIN,
                clock.instant()));
        log.info("Cuenta admin creada: {}", email);
    }
}
