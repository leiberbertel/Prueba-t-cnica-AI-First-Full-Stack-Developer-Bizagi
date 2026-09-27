package dev.leiber.polla.scoring.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.scoring")
record ScoringProperties(int exactPoints, int outcomePoints) {
}
