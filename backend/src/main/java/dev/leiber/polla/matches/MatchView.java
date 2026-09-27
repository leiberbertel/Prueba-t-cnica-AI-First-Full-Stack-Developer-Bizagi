package dev.leiber.polla.matches;

import java.time.Instant;

/** Vista de solo lectura de un partido, expuesta a otros módulos. */
public record MatchView(
        long id,
        String group,
        int matchday,
        TeamView homeTeam,
        TeamView awayTeam,
        Instant kickoffAt,
        String venue,
        MatchStatus status,
        Score result,
        Instant resultRegisteredAt,
        long version) {

    /** RN-02: se aceptan predicciones solo antes del inicio y si el partido no tiene resultado. */
    public boolean isPredictionOpen(Instant now) {
        return status == MatchStatus.SCHEDULED && now.isBefore(kickoffAt);
    }

    public record TeamView(String code, String name, String flagCode) {
    }
}
