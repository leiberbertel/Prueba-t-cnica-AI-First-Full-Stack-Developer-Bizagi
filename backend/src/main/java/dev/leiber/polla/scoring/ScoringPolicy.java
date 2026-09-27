package dev.leiber.polla.scoring;

import dev.leiber.polla.matches.Score;

/**
 * Regla de puntuación (patrón Strategy). Cambiar la regla es cambiar o reemplazar la implementación,
 * sin tocar controladores, persistencia ni el flujo de eventos.
 */
public interface ScoringPolicy {

    int score(Score prediction, Score result);
}
