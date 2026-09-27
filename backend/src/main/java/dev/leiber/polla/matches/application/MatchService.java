package dev.leiber.polla.matches.application;

import java.time.Clock;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leiber.polla.matches.MatchCatalog;
import dev.leiber.polla.matches.MatchResultRegistered;
import dev.leiber.polla.matches.MatchView;
import dev.leiber.polla.matches.Score;
import dev.leiber.polla.matches.domain.Match;
import dev.leiber.polla.matches.infrastructure.MatchRepository;
import dev.leiber.polla.shared.error.ConflictException;
import dev.leiber.polla.shared.error.NotFoundException;

@Service
public class MatchService implements MatchCatalog {

    private final MatchRepository matches;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    MatchService(MatchRepository matches, ApplicationEventPublisher events, Clock clock) {
        this.matches = matches;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchView> findAll() {
        return matches.findAllWithTeams().stream().map(Match::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MatchView get(long matchId) {
        return load(matchId).toView();
    }

    /**
     * Registra o corrige el resultado y publica {@link MatchResultRegistered} en la misma transacción (ADR-0003).
     *
     * @param expectedVersion versión que vio el admin; si otro la cambió antes, se rechaza (CA-03.7). Opcional.
     */
    @Transactional
    public MatchView registerResult(long matchId, Score result, Long expectedVersion) {
        var match = load(matchId);
        if (expectedVersion != null && expectedVersion != match.getVersion()) {
            throw new ConflictException("stale-version",
                    "Otro administrador modificó este partido. Recarga e intenta de nuevo.");
        }
        match.registerResult(result, clock.instant());
        matches.flush(); // incrementa la versión para devolverla actualizada
        events.publishEvent(new MatchResultRegistered(matchId, result));
        return match.toView();
    }

    private Match load(long matchId) {
        return matches.findWithTeams(matchId).orElseThrow(() -> new NotFoundException("Partido", matchId));
    }
}
