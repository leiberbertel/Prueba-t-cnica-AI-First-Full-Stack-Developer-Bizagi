package dev.leiber.polla.predictions.domain;

import java.time.Instant;

import dev.leiber.polla.matches.Score;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "predictions")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Referencias por id a otros módulos (no asociaciones JPA): mantiene los módulos desacoplados.
    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "match_id", nullable = false, updatable = false)
    private long matchId;

    @Column(name = "home_goals", nullable = false)
    private int homeGoals;

    @Column(name = "away_goals", nullable = false)
    private int awayGoals;

    private Integer points;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Prediction() {
    }

    public Prediction(long userId, long matchId, Score score, Instant now) {
        this.userId = userId;
        this.matchId = matchId;
        this.createdAt = now;
        update(score, now);
    }

    public void update(Score score, Instant now) {
        this.homeGoals = score.homeGoals();
        this.awayGoals = score.awayGoals();
        this.updatedAt = now;
    }

    public void assignPoints(int points) {
        this.points = points;
    }

    public Score score() {
        return new Score(homeGoals, awayGoals);
    }

    public long getMatchId() {
        return matchId;
    }

    public Integer getPoints() {
        return points;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
