package dev.leiber.polla.predictions;

import java.util.function.ToIntFunction;

import dev.leiber.polla.matches.Score;

/** API pública del módulo para asignar puntos a las predicciones de un partido. */
public interface PredictionLedger {

    /**
     * Reemplaza los puntos de todas las predicciones del partido con lo que devuelva {@code scorer}.
     * Idempotente: sobrescribe, no acumula (RN-05).
     *
     * @return número de predicciones puntuadas
     */
    int scoreMatch(long matchId, ToIntFunction<Score> scorer);
}
