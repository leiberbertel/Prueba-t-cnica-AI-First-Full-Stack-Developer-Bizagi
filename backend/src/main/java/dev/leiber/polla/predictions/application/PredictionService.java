package dev.leiber.polla.predictions.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leiber.polla.auth.AccountDirectory;
import dev.leiber.polla.matches.MatchCatalog;
import dev.leiber.polla.matches.MatchView;
import dev.leiber.polla.matches.PredictionStatistics;
import dev.leiber.polla.matches.Score;
import dev.leiber.polla.predictions.PredictionLedger;
import dev.leiber.polla.predictions.domain.Prediction;
import dev.leiber.polla.predictions.infrastructure.PredictionRepository;
import dev.leiber.polla.shared.error.ConflictException;
import dev.leiber.polla.shared.error.UnauthorizedException;

@Service
public class PredictionService implements PredictionLedger, PredictionStatistics {

    private final PredictionRepository predictions;
    private final MatchCatalog matches;
    private final AccountDirectory accounts;
    private final Clock clock;

    PredictionService(PredictionRepository predictions, MatchCatalog matches, AccountDirectory accounts,
            Clock clock) {
        this.predictions = predictions;
        this.matches = matches;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** Partidos con la predicción del usuario (CA-02.2). */
    @Transactional(readOnly = true)
    public List<BoardEntry> board(long userId) {
        var now = clock.instant();
        Map<Long, Prediction> mine = predictions.findByUserId(userId).stream()
                .collect(Collectors.toMap(Prediction::getMatchId, Function.identity()));
        return matches.findAll().stream()
                .map(match -> new BoardEntry(match, match.isPredictionOpen(now), toView(mine.get(match.id()))))
                .toList();
    }

    /** Crea o actualiza la predicción (CA-02.3). Rechaza partidos cerrados (RN-02) con la hora del servidor. */
    @Transactional
    public PredictionView upsert(long userId, long matchId, Score score) {
        // CA-01.17: un token emitido antes de eliminar la cuenta no puede volver a crear datos.
        if (!accounts.isActive(userId)) {
            throw new UnauthorizedException("Tu cuenta fue eliminada.");
        }
        var match = matches.get(matchId);
        var now = clock.instant();
        if (!match.isPredictionOpen(now)) {
            throw new ConflictException("prediction-closed",
                    "Las predicciones para este partido están cerradas.");
        }
        var prediction = predictions.findByUserIdAndMatchId(userId, matchId)
                .map(existing -> {
                    existing.update(score, now);
                    return existing;
                })
                .orElseGet(() -> predictions.save(new Prediction(userId, matchId, score, now)));
        return toView(prediction);
    }

    @Override
    @Transactional
    public int scoreMatch(long matchId, ToIntFunction<Score> scorer) {
        var matchPredictions = predictions.findByMatchId(matchId);
        matchPredictions.forEach(prediction -> prediction.assignPoints(scorer.applyAsInt(prediction.score())));
        return matchPredictions.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> countByMatch() {
        return predictions.countGroupedByMatch().stream()
                .collect(Collectors.toMap(PredictionRepository.MatchCount::getMatchId,
                        count -> (int) count.getTotal()));
    }

    private static PredictionView toView(Prediction prediction) {
        if (prediction == null) {
            return null;
        }
        return new PredictionView(prediction.getMatchId(), prediction.score(), prediction.getPoints(),
                prediction.getUpdatedAt());
    }

    public record PredictionView(long matchId, Score score, Integer points, java.time.Instant updatedAt) {
    }

    public record BoardEntry(MatchView match, boolean predictionOpen, PredictionView myPrediction) {
    }
}
