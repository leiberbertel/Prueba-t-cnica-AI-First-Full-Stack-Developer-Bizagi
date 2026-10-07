package dev.leiber.polla.leaderboard.application;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leiber.polla.leaderboard.domain.HistoryItem;
import dev.leiber.polla.leaderboard.domain.Standing;
import dev.leiber.polla.leaderboard.infrastructure.LeaderboardReadRepository;
import dev.leiber.polla.shared.error.NotFoundException;

/** Casos de uso del ranking (specs/04-leaderboard). Las reglas viven aquí; el SQL, en el repositorio. */
@Service
@Transactional(readOnly = true)
public class LeaderboardService {

    private final LeaderboardReadRepository repository;
    private final Clock clock;

    LeaderboardService(LeaderboardReadRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** CA-04.1, CA-04.2. */
    public List<Standing> standings() {
        return repository.findStandings();
    }

    /** CA-04.7. Quien no participa (el admin, RN-09) recibe posición 0. */
    public Summary summary(long userId) {
        int participants = repository.countParticipants();
        int pending = repository.countOpenMatchesWithoutPrediction(userId, clock.instant());
        return repository.findStandingOf(userId)
                .map(s -> new Summary(s.points(), s.position(), s.exactHits(), s.outcomeHits(), pending, participants))
                .orElse(new Summary(0, 0, 0, 0, 0, participants));
    }

    /**
     * CA-04.3, CA-04.4. RN-06: en el historial de otro participante solo se ven partidos cerrados, para que nadie
     * copie predicciones abiertas.
     */
    public History history(long targetUserId, long requesterId) {
        String name = repository.findActiveDisplayName(targetUserId)
                .orElseThrow(() -> new NotFoundException("Usuario", targetUserId));
        boolean ownHistory = targetUserId == requesterId;
        return new History(targetUserId, name, repository.findHistory(targetUserId, ownHistory, clock.instant()));
    }

    public record Summary(int points, int position, int exactHits, int outcomeHits, int pendingPredictions,
            int totalParticipants) {
    }

    public record History(long userId, String displayName, List<HistoryItem> items) {
    }
}
