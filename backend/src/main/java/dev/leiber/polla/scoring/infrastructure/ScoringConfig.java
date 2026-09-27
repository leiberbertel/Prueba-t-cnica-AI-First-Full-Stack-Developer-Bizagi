package dev.leiber.polla.scoring.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.leiber.polla.scoring.ScoringPolicy;
import dev.leiber.polla.scoring.domain.StandardScoringPolicy;

/** Conecta la regla de puntuación (dominio) con sus valores configurables ({@code app.scoring.*}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ScoringProperties.class)
class ScoringConfig {

    @Bean
    ScoringPolicy scoringPolicy(ScoringProperties properties) {
        return new StandardScoringPolicy(properties.exactPoints(), properties.outcomePoints());
    }
}
