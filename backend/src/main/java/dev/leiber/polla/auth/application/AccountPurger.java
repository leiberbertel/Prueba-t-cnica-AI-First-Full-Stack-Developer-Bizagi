package dev.leiber.polla.auth.application;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.leiber.polla.auth.UserAccountDeleted;
import dev.leiber.polla.shared.persistence.BatchDeleter;

/**
 * Fase asíncrona de la eliminación de cuenta en el módulo {@code auth} (ADR-0007):
 * <ol>
 * <li>Al recibir {@link UserAccountDeleted}, borra por lotes los refresh tokens del usuario (sus propios datos).</li>
 * <li>Una tarea programada borra la fila del usuario. La FK {@code RESTRICT} lo impide mientras otro módulo aún
 * tenga datos suyos: en ese caso se reintenta en la siguiente ejecución.</li>
 * </ol>
 * Todo en transacciones cortas con {@code lock_timeout} y {@code statement_timeout}.
 */
@Component
public class AccountPurger {

    private static final Logger log = LoggerFactory.getLogger(AccountPurger.class);
    private static final int FINALIZE_BATCH = 100;

    private final BatchDeleter batchDeleter;
    private final JdbcClient jdbc;

    AccountPurger(BatchDeleter batchDeleter, JdbcClient jdbc) {
        this.batchDeleter = batchDeleter;
        this.jdbc = jdbc;
    }

    /**
     * Sin {@code @Transactional}: cada lote confirma su propia transacción corta. Si un lote falla (timeout),
     * la publicación queda incompleta en el outbox y se reenvía después.
     */
    @Async
    @TransactionalEventListener
    void on(UserAccountDeleted event) {
        int tokens = batchDeleter.deleteInBatches("""
                delete from refresh_tokens where id in (
                    select id from refresh_tokens where user_id = :userId limit :limit for update skip locked)
                """, Map.of("userId", event.userId()));
        log.info("Cuenta {}: {} refresh tokens purgados", event.userId(), tokens);
    }

    /** Borra las filas de cuentas eliminadas que ya no tienen datos en ningún módulo. */
    @Scheduled(fixedDelayString = "${app.account-deletion.finalize-interval}",
            initialDelayString = "${app.account-deletion.finalize-interval}")
    public int finalizePendingDeletions() {
        List<Long> pending = jdbc.sql("select id from users where deleted_at is not null order by deleted_at limit :n")
                .param("n", FINALIZE_BATCH)
                .query(Long.class)
                .list();
        int finalized = 0;
        for (long userId : pending) {
            try {
                finalized += batchDeleter.inShortTransaction(() -> jdbc
                        .sql("delete from users where id = :id and deleted_at is not null")
                        .param("id", userId)
                        .update());
            } catch (DataIntegrityViolationException stillHasData) {
                log.debug("Cuenta {}: aún tiene datos en otros módulos, se reintentará", userId);
            } catch (DataAccessException timeout) {
                // lock_timeout o statement_timeout: falla rápido y se reintenta en la próxima ejecución.
                log.warn("Cuenta {}: no se pudo finalizar ({}); se reintentará", userId, timeout.getClass().getSimpleName());
            }
        }
        if (finalized > 0) {
            log.info("{} cuentas eliminadas definitivamente", finalized);
        }
        return finalized;
    }
}
