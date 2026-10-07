package dev.leiber.polla.predictions.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.leiber.polla.auth.UserAccountDeleted;
import dev.leiber.polla.predictions.infrastructure.PredictionPurgeRepository;

/**
 * Borra las predicciones de una cuenta eliminada (ADR-0007). El módulo {@code predictions} es dueño de sus datos:
 * {@code auth} no los toca.
 */
@Component
class PredictionPurger {

    private static final Logger log = LoggerFactory.getLogger(PredictionPurger.class);

    private final PredictionPurgeRepository repository;

    PredictionPurger(PredictionPurgeRepository repository) {
        this.repository = repository;
    }

    /**
     * Sin {@code @Transactional}: cada lote confirma su propia transacción corta. Si quedan filas (estaban bloqueadas
     * por otra transacción), se lanza una excepción para que la publicación quede incompleta y se reintente después.
     */
    @Async
    @TransactionalEventListener
    void on(UserAccountDeleted event) {
        int purged = repository.purgeByUser(event.userId());
        int remaining = repository.countByUser(event.userId());
        if (remaining > 0) {
            throw new IllegalStateException("Cuenta %d: %d predicciones bloqueadas; se reintentará"
                    .formatted(event.userId(), remaining));
        }
        log.info("Cuenta {}: {} predicciones purgadas", event.userId(), purged);
    }
}
