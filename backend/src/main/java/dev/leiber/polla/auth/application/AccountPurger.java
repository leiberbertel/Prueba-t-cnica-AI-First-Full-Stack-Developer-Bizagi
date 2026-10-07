package dev.leiber.polla.auth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.leiber.polla.auth.UserAccountDeleted;
import dev.leiber.polla.auth.infrastructure.AccountPurgeRepository;

/**
 * Fase asíncrona de la eliminación de cuenta en el módulo {@code auth} (ADR-0007):
 * <ol>
 * <li>Al recibir {@link UserAccountDeleted}, borra por lotes los refresh tokens del usuario (sus propios datos).</li>
 * <li>Una tarea programada borra la fila del usuario. La FK {@code RESTRICT} lo impide mientras otro módulo aún
 * tenga datos suyos: en ese caso se reintenta en la siguiente ejecución.</li>
 * </ol>
 */
@Component
public class AccountPurger {

    private static final Logger log = LoggerFactory.getLogger(AccountPurger.class);
    private static final int FINALIZE_BATCH = 100;

    private final AccountPurgeRepository repository;

    AccountPurger(AccountPurgeRepository repository) {
        this.repository = repository;
    }

    /**
     * Sin {@code @Transactional}: cada lote confirma su propia transacción corta. Si un lote falla (timeout),
     * la publicación queda incompleta en el outbox y se reenvía después.
     */
    @Async
    @TransactionalEventListener
    void on(UserAccountDeleted event) {
        int tokens = repository.purgeRefreshTokensOf(event.userId());
        log.info("Cuenta {}: {} refresh tokens purgados", event.userId(), tokens);
    }

    /** Borra las filas de cuentas eliminadas que ya no tienen datos en ningún módulo. */
    @Scheduled(fixedDelayString = "${app.account-deletion.finalize-interval}",
            initialDelayString = "${app.account-deletion.finalize-interval}")
    public int finalizePendingDeletions() {
        int finalized = 0;
        for (long userId : repository.findPendingDeletions(FINALIZE_BATCH)) {
            try {
                finalized += repository.deleteAccountRow(userId);
            } catch (DataIntegrityViolationException stillHasData) {
                log.debug("Cuenta {}: aún tiene datos en otros módulos, se reintentará", userId);
            } catch (DataAccessException timeout) {
                // lock_timeout o statement_timeout: falla rápido y se reintenta en la próxima ejecución.
                log.warn("Cuenta {}: no se pudo finalizar ({}); se reintentará", userId,
                        timeout.getClass().getSimpleName());
            }
        }
        if (finalized > 0) {
            log.info("{} cuentas eliminadas definitivamente", finalized);
        }
        return finalized;
    }
}
