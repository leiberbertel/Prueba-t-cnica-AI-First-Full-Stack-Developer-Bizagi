package dev.leiber.polla.auth.infrastructure;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.leiber.polla.shared.persistence.BatchDeleter;

/**
 * Operaciones masivas de {@code auth} en SQL nativo (ADR-0008): borrado por lotes con {@code SKIP LOCKED} y en
 * transacciones cortas con timeouts (ADR-0007).
 */
@Repository
public class AccountPurgeRepository {

    private final BatchDeleter batchDeleter;
    private final JdbcClient jdbc;

    AccountPurgeRepository(BatchDeleter batchDeleter, JdbcClient jdbc) {
        this.batchDeleter = batchDeleter;
        this.jdbc = jdbc;
    }

    public int purgeRefreshTokensOf(long userId) {
        return batchDeleter.deleteInBatches("""
                delete from refresh_tokens where id in (
                    select id from refresh_tokens where user_id = :userId limit :limit for update skip locked)
                """, Map.of("userId", userId));
    }

    public int purgeRefreshTokensExpiredBefore(Instant cutoff) {
        return batchDeleter.deleteInBatches("""
                delete from refresh_tokens where id in (
                    select id from refresh_tokens where expires_at < :cutoff limit :limit for update skip locked)
                """, Map.of("cutoff", cutoff.atOffset(ZoneOffset.UTC)));
    }

    /** Cuentas marcadas como eliminadas cuya fila todavía existe, las más antiguas primero. */
    public List<Long> findPendingDeletions(int limit) {
        return jdbc.sql("select id from users where deleted_at is not null order by deleted_at limit :n")
                .param("n", limit)
                .query(Long.class)
                .list();
    }

    /**
     * Borra la fila de una cuenta eliminada en una transacción corta con timeouts. La FK {@code RESTRICT} hace que
     * falle mientras otro módulo aún tenga datos del usuario.
     *
     * @return filas borradas (0 o 1)
     */
    public int deleteAccountRow(long userId) {
        return batchDeleter.inShortTransaction(() -> jdbc
                .sql("delete from users where id = :id and deleted_at is not null")
                .param("id", userId)
                .update());
    }
}
