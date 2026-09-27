package dev.leiber.polla.matches;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * Evento de dominio: el admin registró (o corrigió) el resultado real de un partido (ADR-0003).
 */
@DomainEvent
public record MatchResultRegistered(long matchId, Score result) {
}
