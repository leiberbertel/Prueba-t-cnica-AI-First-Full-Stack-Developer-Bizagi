package dev.leiber.polla.matches.internal;

import java.time.Instant;

import dev.leiber.polla.matches.MatchStatus;
import dev.leiber.polla.matches.MatchView;
import dev.leiber.polla.matches.Score;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "matches")
class Match {

    @Id
    private Long id;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    @Column(nullable = false)
    private int matchday;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @Column(name = "kickoff_at", nullable = false)
    private Instant kickoffAt;

    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Column(name = "home_goals")
    private Integer homeGoals;

    @Column(name = "away_goals")
    private Integer awayGoals;

    @Column(name = "result_registered_at")
    private Instant resultRegisteredAt;

    @Version
    private long version;

    protected Match() {
    }

    /** Registra o corrige el resultado. El partido queda FINISHED y cerrado a predicciones. */
    void registerResult(Score result, Instant now) {
        this.homeGoals = result.homeGoals();
        this.awayGoals = result.awayGoals();
        this.status = MatchStatus.FINISHED;
        this.resultRegisteredAt = now;
    }

    long getVersion() {
        return version;
    }

    MatchView toView() {
        Score result = status == MatchStatus.FINISHED ? new Score(homeGoals, awayGoals) : null;
        return new MatchView(id, groupCode, matchday, team(homeTeam), team(awayTeam), kickoffAt, venue, status,
                result, resultRegisteredAt, version);
    }

    private static MatchView.TeamView team(Team team) {
        return new MatchView.TeamView(team.getCode(), team.getName(), team.getFlagCode());
    }
}
