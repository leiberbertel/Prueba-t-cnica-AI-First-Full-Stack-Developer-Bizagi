package dev.leiber.polla.scoring.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.scoring")
record ScoringProperties(int exactPoints, int outcomePoints) {
}
