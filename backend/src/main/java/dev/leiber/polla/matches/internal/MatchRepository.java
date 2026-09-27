package dev.leiber.polla.matches.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam order by m.kickoffAt, m.id")
    List<Match> findAllWithTeams();

    @Query("select m from Match m join fetch m.homeTeam join fetch m.awayTeam where m.id = :id")
    Optional<Match> findWithTeams(long id);
}
