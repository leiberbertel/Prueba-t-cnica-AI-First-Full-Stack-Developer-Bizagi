package dev.leiber.polla.scoring.domain;

import dev.leiber.polla.matches.Score;
import dev.leiber.polla.scoring.ScoringPolicy;

/**
 * RN-04: exacto = {@code exactPoints}, mismo desenlace (ganador o empate) = {@code outcomePoints}, fallo = 0.
 * <p>
 * Java puro, sin dependencias de Spring: la regla de negocio se prueba y se entiende sin el framework.
 * La instancia la crea {@code ScoringConfig} con los valores configurados.
 */
public class StandardScoringPolicy implements ScoringPolicy {

    private final int exactPoints;
    private final int outcomePoints;

    public StandardScoringPolicy(int exactPoints, int outcomePoints) {
        this.exactPoints = exactPoints;
        this.outcomePoints = outcomePoints;
    }

    @Override
    public int score(Score prediction, Score result) {
        if (prediction.equals(result)) {
            return exactPoints;
        }
        if (prediction.outcome() == result.outcome()) {
            return outcomePoints;
        }
        return 0;
    }
}
