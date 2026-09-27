package dev.leiber.polla.scoring.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import dev.leiber.polla.matches.Score;
import dev.leiber.polla.scoring.ScoringPolicy;

/** RN-04: exacto = 3, mismo desenlace (ganador o empate) = 1, fallo = 0. Valores configurables. */
@Component
@EnableConfigurationProperties(ScoringProperties.class)
class StandardScoringPolicy implements ScoringPolicy {

    private final ScoringProperties properties;

    StandardScoringPolicy(ScoringProperties properties) {
        this.properties = properties;
    }

    @Override
    public int score(Score prediction, Score result) {
        if (prediction.equals(result)) {
            return properties.exactPoints();
        }
        if (prediction.outcome() == result.outcome()) {
            return properties.outcomePoints();
        }
        return 0;
    }
}
