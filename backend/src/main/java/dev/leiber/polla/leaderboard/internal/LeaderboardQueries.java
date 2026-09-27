package dev.leiber.polla.leaderboard.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leiber.polla.matches.MatchStatus;
import dev.leiber.polla.matches.MatchView.TeamView;
import dev.leiber.polla.matches.Score;
import dev.leiber.polla.shared.error.NotFoundException;

/**
 * Consultas de lectura del ranking. Una sola consulta agregada con DENSE_RANK (specs/04-leaderboard).
 * <p>
 * Los aciertos se derivan de los marcadores (no de los puntos), así que siguen siendo correctos aunque cambien
 * los valores de la regla de puntuación.
 */
@Service
@Transactional(readOnly = true)
public class LeaderboardQueries {

    private static final String STANDINGS_SQL = """
            with stats as (
                select u.id                                   as user_id,
                       u.display_name                         as display_name,
                       coalesce(sum(p.points), 0)             as points,
                       count(*) filter (where m.status = 'FINISHED'
                                          and p.home_goals = m.home_goals
                                          and p.away_goals = m.away_goals) as exact_hits,
                       count(*) filter (where m.status = 'FINISHED'
                                          and not (p.home_goals = m.home_goals and p.away_goals = m.away_goals)
                                          and sign(p.home_goals - p.away_goals) = sign(m.home_goals - m.away_goals))
                                                              as outcome_hits,
                       count(p.points)                        as scored_predictions
                from users u
                left join predictions p on p.user_id = u.id
                left join matches m on m.id = p.match_id
                where u.role = 'USER'
                group by u.id, u.display_name
            )
            select dense_rank() over (order by points desc, exact_hits desc, outcome_hits desc) as position,
                   user_id, display_name, points, exact_hits, outcome_hits, scored_predictions
            from stats
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    LeaderboardQueries(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** RN-07: puntos ↓, exactos ↓, ganador ↓, nombre ↑. Empatados comparten posición. */
    public List<Standing> standings() {
        return jdbc.sql(STANDINGS_SQL + " order by position, lower(display_name), user_id")
                .query(LeaderboardQueries::mapStanding)
                .list();
    }

    public Summary summary(long userId) {
        var mine = jdbc.sql("select * from (" + STANDINGS_SQL + ") ranked where user_id = :userId")
                .param("userId", userId)
                .query(LeaderboardQueries::mapStanding)
                .optional();
        int participants = jdbc.sql("select count(*) from users where role = 'USER'").query(Integer.class).single();
        int pending = jdbc.sql("""
                select count(*) from matches m
                where m.status = 'SCHEDULED' and m.kickoff_at > :now
                  and not exists (select 1 from predictions p where p.match_id = m.id and p.user_id = :userId)
                """)
                .param("now", utc(clock.instant()))
                .param("userId", userId)
                .query(Integer.class)
                .single();
        return mine
                .map(s -> new Summary(s.points(), s.position(), s.exactHits(), s.outcomeHits(), pending, participants))
                .orElse(new Summary(0, 0, 0, 0, 0, participants)); // no participa (admin)
    }

    /**
     * Historial de predicciones. Si quien consulta no es el dueño, solo se muestran partidos cerrados (RN-06).
     */
    public History history(long targetUserId, long requesterId) {
        String name = findDisplayName(targetUserId)
                .orElseThrow(() -> new NotFoundException("Usuario", targetUserId));
        boolean ownHistory = targetUserId == requesterId;
        var items = jdbc.sql("""
                select m.id as match_id, m.group_code, m.kickoff_at, m.status, m.home_goals as real_home,
                       m.away_goals as real_away, p.home_goals as pred_home, p.away_goals as pred_away, p.points,
                       ht.code as home_code, ht.name as home_name, ht.flag_code as home_flag,
                       at.code as away_code, at.name as away_name, at.flag_code as away_flag
                from predictions p
                join matches m on m.id = p.match_id
                join teams ht on ht.id = m.home_team_id
                join teams at on at.id = m.away_team_id
                where p.user_id = :userId
                  and (:own or m.status = 'FINISHED' or m.kickoff_at <= :now)
                order by m.kickoff_at, m.id
                """)
                .param("userId", targetUserId)
                .param("own", ownHistory)
                .param("now", utc(clock.instant()))
                .query(LeaderboardQueries::mapHistoryItem)
                .list();
        return new History(targetUserId, name, items);
    }

    private Optional<String> findDisplayName(long userId) {
        return jdbc.sql("select display_name from users where id = :id")
                .param("id", userId)
                .query(String.class)
                .optional();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Standing mapStanding(ResultSet rs, int row) throws SQLException {
        return new Standing(rs.getInt("position"), rs.getLong("user_id"), rs.getString("display_name"),
                rs.getInt("points"), rs.getInt("exact_hits"), rs.getInt("outcome_hits"),
                rs.getInt("scored_predictions"));
    }

    private static HistoryItem mapHistoryItem(ResultSet rs, int row) throws SQLException {
        var status = MatchStatus.valueOf(rs.getString("status"));
        Score result = status == MatchStatus.FINISHED ? new Score(rs.getInt("real_home"), rs.getInt("real_away"))
                : null;
        Integer points = (Integer) rs.getObject("points", Integer.class);
        return new HistoryItem(
                rs.getLong("match_id"),
                rs.getString("group_code"),
                new TeamView(rs.getString("home_code"), rs.getString("home_name"), rs.getString("home_flag")),
                new TeamView(rs.getString("away_code"), rs.getString("away_name"), rs.getString("away_flag")),
                rs.getObject("kickoff_at", OffsetDateTime.class).toInstant(),
                status,
                result,
                new Score(rs.getInt("pred_home"), rs.getInt("pred_away")),
                points);
    }

    public record Standing(int position, long userId, String displayName, int points, int exactHits,
            int outcomeHits, int scoredPredictions) {
    }

    public record Summary(int points, int position, int exactHits, int outcomeHits, int pendingPredictions,
            int totalParticipants) {
    }

    public record HistoryItem(long matchId, String group, TeamView homeTeam, TeamView awayTeam, Instant kickoffAt,
            MatchStatus status, Score result, Score prediction, Integer points) {
    }

    public record History(long userId, String displayName, List<HistoryItem> items) {
    }
}
