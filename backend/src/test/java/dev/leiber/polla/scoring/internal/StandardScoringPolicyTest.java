package dev.leiber.polla.scoring.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.leiber.polla.matches.Score;

/** Tabla de verdad de RN-04 (specs/02-predictions/spec.md). */
class StandardScoringPolicyTest {

    private final StandardScoringPolicy policy = new StandardScoringPolicy(new ScoringProperties(3, 1));

    @ParameterizedTest(name = "predicción {0}-{1} vs real {2}-{3} → {4} puntos ({5})")
    @CsvSource({
            "2, 1, 2, 1, 3, exacto",
            "0, 0, 0, 0, 3, exacto con empate",
            "3, 1, 2, 0, 1, gana local",
            "1, 1, 2, 2, 1, empate",
            "0, 2, 1, 3, 1, gana visitante",
            "2, 1, 1, 2, 0, desenlace invertido",
            "1, 1, 1, 0, 0, empate vs victoria",
            "0, 1, 0, 0, 0, victoria vs empate",
    })
    void scoresAccordingToSpec(int predHome, int predAway, int realHome, int realAway, int expected, String reason) {
        assertThat(policy.score(new Score(predHome, predAway), new Score(realHome, realAway))).isEqualTo(expected);
    }

    @Test
    void usesConfiguredPointValues() {
        var custom = new StandardScoringPolicy(new ScoringProperties(5, 2));

        assertThat(custom.score(new Score(1, 0), new Score(1, 0))).isEqualTo(5);
        assertThat(custom.score(new Score(2, 0), new Score(1, 0))).isEqualTo(2);
    }
}
