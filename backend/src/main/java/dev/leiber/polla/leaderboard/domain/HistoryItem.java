package dev.leiber.polla.leaderboard.domain;

import java.time.Instant;

import dev.leiber.polla.matches.MatchStatus;
import dev.leiber.polla.matches.MatchView.TeamView;
import dev.leiber.polla.matches.Score;

/** Una predicción del historial de un participante (modelo de lectura). {@code result} y {@code points} son null
 * mientras el partido no tenga resultado. */
public record HistoryItem(long matchId, String group, TeamView homeTeam, TeamView awayTeam, Instant kickoffAt,
        MatchStatus status, Score result, Score prediction, Integer points) {
}
