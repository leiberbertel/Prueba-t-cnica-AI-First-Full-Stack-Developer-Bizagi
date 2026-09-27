package dev.leiber.polla.predictions.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dev.leiber.polla.predictions.domain.Prediction;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findByUserIdAndMatchId(long userId, long matchId);

    List<Prediction> findByUserId(long userId);

    List<Prediction> findByMatchId(long matchId);

    @Query("select p.matchId as matchId, count(p) as total from Prediction p group by p.matchId")
    List<MatchCount> countGroupedByMatch();

    interface MatchCount {
        long getMatchId();

        long getTotal();
    }
}
