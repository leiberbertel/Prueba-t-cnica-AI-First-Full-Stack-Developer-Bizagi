package dev.leiber.polla.matches.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.leiber.polla.matches.MatchStatus;
import dev.leiber.polla.matches.MatchView;
import dev.leiber.polla.matches.PredictionStatistics;
import dev.leiber.polla.matches.Score;
import dev.leiber.polla.matches.application.MatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Panel de administración (specs/03-admin-results). Doble protección: URL en SecurityConfig + método. */
@RestController
@RequestMapping("/api/v1/admin/matches")
@PreAuthorize("hasRole('ADMIN')")
class AdminMatchesController {

    private final MatchService matchService;
    private final ObjectProvider<PredictionStatistics> predictionStatistics;

    AdminMatchesController(MatchService matchService, ObjectProvider<PredictionStatistics> predictionStatistics) {
        this.matchService = matchService;
        this.predictionStatistics = predictionStatistics;
    }

    @GetMapping
    List<AdminMatchResponse> list() {
        var counts = countsByMatch();
        return matchService.findAll().stream()
                .map(match -> AdminMatchResponse.from(match, counts.getOrDefault(match.id(), 0)))
                .toList();
    }

    @PutMapping("/{matchId}/result")
    AdminMatchResponse registerResult(@PathVariable long matchId, @Valid @RequestBody RegisterResultRequest request) {
        var match = matchService.registerResult(matchId, new Score(request.homeGoals(), request.awayGoals()),
                request.version());
        return AdminMatchResponse.from(match, countsByMatch().getOrDefault(matchId, 0));
    }

    private Map<Long, Integer> countsByMatch() {
        var statistics = predictionStatistics.getIfAvailable();
        return statistics == null ? Map.of() : statistics.countByMatch();
    }

    record RegisterResultRequest(
            @NotNull(message = "Obligatorio") @Min(value = 0, message = "Mínimo 0")
            @Max(value = Score.MAX_GOALS, message = "Máximo 20") Integer homeGoals,
            @NotNull(message = "Obligatorio") @Min(value = 0, message = "Mínimo 0")
            @Max(value = Score.MAX_GOALS, message = "Máximo 20") Integer awayGoals,
            Long version) {
    }

    record AdminMatchResponse(long id, String group, int matchday, MatchView.TeamView homeTeam,
            MatchView.TeamView awayTeam, Instant kickoffAt, MatchStatus status, Score result,
            Instant resultRegisteredAt, int predictionsCount, long version) {

        static AdminMatchResponse from(MatchView match, int predictionsCount) {
            return new AdminMatchResponse(match.id(), match.group(), match.matchday(), match.homeTeam(),
                    match.awayTeam(), match.kickoffAt(), match.status(), match.result(), match.resultRegisteredAt(),
                    predictionsCount, match.version());
        }
    }
}
