package dev.leiber.polla.predictions.infrastructure;

import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.leiber.polla.shared.persistence.BatchDeleter;

/**
 * Borrado masivo de predicciones en SQL nativo (ADR-0008): {@code LIMIT} y {@code FOR UPDATE SKIP LOCKED} dentro de
 * un {@code DELETE} no se pueden expresar con JPA. Cada lote es una transacción corta con timeouts (ADR-0007).
 */
@Repository
public class PredictionPurgeRepository {

    private final BatchDeleter batchDeleter;
    private final JdbcClient jdbc;

    PredictionPurgeRepository(BatchDeleter batchDeleter, JdbcClient jdbc) {
        this.batchDeleter = batchDeleter;
        this.jdbc = jdbc;
    }

    /** Borra por lotes las predicciones del usuario. Las filas bloqueadas por otra transacción se saltan. */
    public int purgeByUser(long userId) {
        return batchDeleter.deleteInBatches("""
                delete from predictions where id in (
                    select id from predictions where user_id = :userId limit :limit for update skip locked)
                """, Map.of("userId", userId));
    }

    public int countByUser(long userId) {
        return jdbc.sql("select count(*) from predictions where user_id = :userId")
                .param("userId", userId)
                .query(Integer.class)
                .single();
    }
}
