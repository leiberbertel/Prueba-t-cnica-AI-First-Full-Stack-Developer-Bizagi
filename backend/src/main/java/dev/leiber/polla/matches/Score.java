package dev.leiber.polla.matches;

/** Marcador de un partido (real o pronosticado). */
public record Score(int homeGoals, int awayGoals) {

    public static final int MAX_GOALS = 20;

    public Score {
        if (homeGoals < 0 || awayGoals < 0 || homeGoals > MAX_GOALS || awayGoals > MAX_GOALS) {
            throw new IllegalArgumentException("Goles fuera de rango: %d-%d".formatted(homeGoals, awayGoals));
        }
    }

    /** 1 = gana local, 0 = empate, -1 = gana visitante. */
    public int outcome() {
        return Integer.signum(homeGoals - awayGoals);
    }
}
