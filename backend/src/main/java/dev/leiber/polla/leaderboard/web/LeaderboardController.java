package dev.leiber.polla.leaderboard.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.leiber.polla.leaderboard.application.LeaderboardService;
import dev.leiber.polla.leaderboard.application.LeaderboardService.History;
import dev.leiber.polla.leaderboard.application.LeaderboardService.Summary;
import dev.leiber.polla.leaderboard.domain.HistoryItem;
import dev.leiber.polla.leaderboard.domain.Standing;
import dev.leiber.polla.shared.security.CurrentUser;

@RestController
@RequestMapping("/api/v1")
class LeaderboardController {

    private final LeaderboardService leaderboard;

    LeaderboardController(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    @GetMapping("/leaderboard")
    List<Standing> leaderboard() {
        return leaderboard.standings();
    }

    @GetMapping("/me/summary")
    Summary mySummary(@AuthenticationPrincipal Jwt jwt) {
        return leaderboard.summary(CurrentUser.id(jwt));
    }

    @GetMapping("/users/{userId}/predictions")
    HistoryResponse history(@AuthenticationPrincipal Jwt jwt, @PathVariable long userId) {
        return HistoryResponse.from(leaderboard.history(userId, CurrentUser.id(jwt)));
    }

    record HistoryResponse(UserRef user, List<HistoryItem> items) {

        static HistoryResponse from(History history) {
            return new HistoryResponse(new UserRef(history.userId(), history.displayName()), history.items());
        }
    }

    record UserRef(long id, String displayName) {
    }
}
