package dev.leiber.polla.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.leiber.polla.shared.persistence.BatchDeleter;

/**
 * Purga por lotes los refresh tokens vencidos (CA-01.19). Cada renovación de sesión crea una fila, así que sin esta
 * limpieza la tabla crecería sin límite. Se conservan un día después de vencer como margen de diagnóstico.
 */
@Component
public class RefreshTokenJanitor {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenJanitor.class);
    private static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    private final BatchDeleter batchDeleter;
    private final Clock clock;

    RefreshTokenJanitor(BatchDeleter batchDeleter, Clock clock) {
        this.batchDeleter = batchDeleter;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.refresh-token-cleanup.interval:PT1H}", initialDelay = 60_000)
    public int purgeExpired() {
        var cutoff = clock.instant().minus(RETENTION_AFTER_EXPIRY).atOffset(ZoneOffset.UTC);
        int purged = batchDeleter.deleteInBatches("""
                delete from refresh_tokens where id in (
                    select id from refresh_tokens where expires_at < :cutoff limit :limit for update skip locked)
                """, Map.of("cutoff", cutoff));
        if (purged > 0) {
            log.info("{} refresh tokens vencidos purgados", purged);
        }
        return purged;
    }
}
