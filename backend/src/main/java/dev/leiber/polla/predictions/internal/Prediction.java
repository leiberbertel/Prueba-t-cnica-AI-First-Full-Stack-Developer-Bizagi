package dev.leiber.polla.predictions.internal;

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
class Prediction {

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

    Prediction(long userId, long matchId, Score score, Instant now) {
        this.userId = userId;
        this.matchId = matchId;
        this.createdAt = now;
        update(score, now);
    }

    void update(Score score, Instant now) {
        this.homeGoals = score.homeGoals();
        this.awayGoals = score.awayGoals();
        this.updatedAt = now;
    }

    void assignPoints(int points) {
        this.points = points;
    }

    Score score() {
        return new Score(homeGoals, awayGoals);
    }

    long getMatchId() {
        return matchId;
    }

    Integer getPoints() {
        return points;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
