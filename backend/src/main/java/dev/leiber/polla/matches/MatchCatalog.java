package dev.leiber.polla.matches;

import java.util.List;

/** API pública del módulo para consultar partidos. */
public interface MatchCatalog {

    /** Todos los partidos ordenados por fecha de inicio. */
    List<MatchView> findAll();

    /** @throws dev.leiber.polla.shared.error.NotFoundException si el partido no existe */
    MatchView get(long matchId);
}
