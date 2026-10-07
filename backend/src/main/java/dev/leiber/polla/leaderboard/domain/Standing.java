package dev.leiber.polla.leaderboard.domain;

/** Fila del ranking (modelo de lectura). Empatados en todos los criterios comparten {@code position} (RN-07). */
public record Standing(int position, long userId, String displayName, int points, int exactHits, int outcomeHits,
        int scoredPredictions) {
}
