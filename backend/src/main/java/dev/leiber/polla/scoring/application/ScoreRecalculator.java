package dev.leiber.polla.scoring.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import dev.leiber.polla.matches.MatchResultRegistered;
import dev.leiber.polla.predictions.PredictionLedger;
import dev.leiber.polla.scoring.ScoringPolicy;

/**
 * Recalcula los puntos cuando se registra o corrige un resultado (RN-05, ADR-0003).
 * <p>
 * {@code @ApplicationModuleListener} = asíncrono, después del commit, en su propia transacción y con la publicación
 * persistida en el Event Publication Registry: si falla, el evento se reintenta. Por eso la operación es idempotente.
 */
@Component
class ScoreRecalculator {

    private static final Logger log = LoggerFactory.getLogger(ScoreRecalculator.class);

    private final PredictionLedger ledger;
    private final ScoringPolicy policy;

    ScoreRecalculator(PredictionLedger ledger, ScoringPolicy policy) {
        this.ledger = ledger;
        this.policy = policy;
    }

    @ApplicationModuleListener
    void on(MatchResultRegistered event) {
        int scored = ledger.scoreMatch(event.matchId(), prediction -> policy.score(prediction, event.result()));
        log.info("Partido {} ({}-{}): {} predicciones puntuadas", event.matchId(), event.result().homeGoals(),
                event.result().awayGoals(), scored);
    }
}
