package dev.leiber.polla.shared.events;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reenvía los eventos cuyo listener falló (p. ej. un lote de purga que excedió su timeout). Sin esto, el
 * Event Publication Registry solo los reintentaría al reiniciar la aplicación (ADR-0003, ADR-0007).
 * Los listeners son idempotentes, así que reprocesar es seguro.
 */
@Component
class EventPublicationRetrier {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationRetrier.class);

    private final IncompleteEventPublications incompletePublications;
    private final Duration olderThan;

    EventPublicationRetrier(IncompleteEventPublications incompletePublications,
            @Value("${app.events.retry-older-than:PT5M}") Duration olderThan) {
        this.incompletePublications = incompletePublications;
        this.olderThan = olderThan;
    }

    @Scheduled(fixedDelayString = "${app.events.retry-interval:PT5M}",
            initialDelayString = "${app.events.retry-interval:PT5M}")
    void resubmitIncomplete() {
        log.debug("Reenviando publicaciones de eventos incompletas con más de {}", olderThan);
        incompletePublications.resubmitIncompletePublicationsOlderThan(olderThan);
    }
}
