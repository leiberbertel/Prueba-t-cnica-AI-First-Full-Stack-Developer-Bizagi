package dev.leiber.polla.predictions.web;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.leiber.polla.matches.MatchStatus;
import dev.leiber.polla.matches.MatchView.TeamView;
import dev.leiber.polla.matches.Score;
import dev.leiber.polla.predictions.application.PredictionService;
import dev.leiber.polla.predictions.application.PredictionService.BoardEntry;
import dev.leiber.polla.predictions.application.PredictionService.PredictionView;
import dev.leiber.polla.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/matches")
class MatchBoardController {

    private final PredictionService predictionService;

    MatchBoardController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping
    List<MatchResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return predictionService.board(CurrentUser.id(jwt)).stream().map(MatchResponse::from).toList();
    }

    /** RN-09: solo participantes (USER) predicen; el admin no. */
    @PutMapping("/{matchId}/prediction")
    @PreAuthorize("hasRole('USER')")
    PredictionResponse upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable long matchId,
            @Valid @RequestBody ScoreRequest request) {
        var saved = predictionService.upsert(CurrentUser.id(jwt), matchId,
                new Score(request.homeGoals(), request.awayGoals()));
        return PredictionResponse.from(saved);
    }

    record ScoreRequest(
            @NotNull(message = "Obligatorio") @Min(value = 0, message = "Mínimo 0")
            @Max(value = Score.MAX_GOALS, message = "Máximo 20") Integer homeGoals,
            @NotNull(message = "Obligatorio") @Min(value = 0, message = "Mínimo 0")
            @Max(value = Score.MAX_GOALS, message = "Máximo 20") Integer awayGoals) {
    }

    record PredictionResponse(long matchId, int homeGoals, int awayGoals, Integer points, Instant updatedAt) {

        static PredictionResponse from(PredictionView view) {
            if (view == null) {
                return null;
            }
            return new PredictionResponse(view.matchId(), view.score().homeGoals(), view.score().awayGoals(),
                    view.points(), view.updatedAt());
        }
    }

    record MatchResponse(long id, String group, int matchday, TeamView homeTeam, TeamView awayTeam,
            Instant kickoffAt, String venue, MatchStatus status, Score result, boolean predictionOpen,
            PredictionResponse myPrediction) {

        static MatchResponse from(BoardEntry entry) {
            var match = entry.match();
            return new MatchResponse(match.id(), match.group(), match.matchday(), match.homeTeam(), match.awayTeam(),
                    match.kickoffAt(), match.venue(), match.status(), match.result(), entry.predictionOpen(),
                    PredictionResponse.from(entry.myPrediction()));
        }
    }
}
