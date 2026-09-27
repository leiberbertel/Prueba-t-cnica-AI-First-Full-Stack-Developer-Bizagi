package dev.leiber.polla.predictions.application;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.leiber.polla.auth.UserAccountDeleted;
import dev.leiber.polla.shared.persistence.BatchDeleter;

/**
 * Borra por lotes las predicciones de una cuenta eliminada (ADR-0007). El módulo {@code predictions} es dueño de sus
 * datos: {@code auth} no los toca.
 */
@Component
class PredictionPurger {

    private static final Logger log = LoggerFactory.getLogger(PredictionPurger.class);

    private final BatchDeleter batchDeleter;
    private final JdbcClient jdbc;

    PredictionPurger(BatchDeleter batchDeleter, JdbcClient jdbc) {
        this.batchDeleter = batchDeleter;
        this.jdbc = jdbc;
    }

    /**
     * Sin {@code @Transactional}: cada lote confirma su propia transacción corta. {@code SKIP LOCKED} no espera filas
     * bloqueadas por otra transacción; si al final quedan filas, se lanza una excepción para que la publicación quede
     * incompleta y se reintente después.
     */
    @Async
    @TransactionalEventListener
    void on(UserAccountDeleted event) {
        int purged = batchDeleter.deleteInBatches("""
                delete from predictions where id in (
                    select id from predictions where user_id = :userId limit :limit for update skip locked)
                """, Map.of("userId", event.userId()));
        int remaining = jdbc.sql("select count(*) from predictions where user_id = :userId")
                .param("userId", event.userId())
                .query(Integer.class)
                .single();
        if (remaining > 0) {
            throw new IllegalStateException("Cuenta %d: %d predicciones bloqueadas; se reintentará"
                    .formatted(event.userId(), remaining));
        }
        log.info("Cuenta {}: {} predicciones purgadas", event.userId(), purged);
    }
}
