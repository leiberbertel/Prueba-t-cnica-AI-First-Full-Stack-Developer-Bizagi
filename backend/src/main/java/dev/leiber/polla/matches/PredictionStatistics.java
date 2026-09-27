package dev.leiber.polla.matches;

import java.util.Map;

/**
 * Puerto (SPI) que implementa el módulo de predicciones. Así {@code matches} puede mostrar cuántas predicciones
 * tiene cada partido sin depender de {@code predictions} (inversión de dependencias, sin ciclos).
 */
public interface PredictionStatistics {

    /** Número de predicciones por id de partido. Los partidos sin predicciones pueden no aparecer. */
    Map<Long, Integer> countByMatch();
}
