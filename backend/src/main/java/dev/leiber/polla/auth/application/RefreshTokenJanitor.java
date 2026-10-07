package dev.leiber.polla.auth.application;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.leiber.polla.auth.infrastructure.AccountPurgeRepository;

/**
 * Purga por lotes los refresh tokens vencidos (CA-01.19). Cada renovación de sesión crea una fila, así que sin esta
 * limpieza la tabla crecería sin límite. Se conservan un día después de vencer como margen de diagnóstico.
 */
@Component
public class RefreshTokenJanitor {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenJanitor.class);
    private static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    private final AccountPurgeRepository repository;
    private final Clock clock;

    RefreshTokenJanitor(AccountPurgeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.refresh-token-cleanup.interval:PT1H}", initialDelay = 60_000)
    public int purgeExpired() {
        int purged = repository.purgeRefreshTokensExpiredBefore(clock.instant().minus(RETENTION_AFTER_EXPIRY));
        if (purged > 0) {
            log.info("{} refresh tokens vencidos purgados", purged);
        }
        return purged;
    }
}
