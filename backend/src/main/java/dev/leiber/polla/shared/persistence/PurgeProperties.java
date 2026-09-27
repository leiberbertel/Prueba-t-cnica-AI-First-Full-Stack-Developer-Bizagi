package dev.leiber.polla.shared.persistence;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de las operaciones de purga por lotes (ADR-0007).
 *
 * @param batchSize        filas por transacción
 * @param lockTimeout      máximo esperando un bloqueo antes de fallar
 * @param statementTimeout máximo por sentencia antes de fallar
 */
@ConfigurationProperties("app.purge")
public record PurgeProperties(int batchSize, Duration lockTimeout, Duration statementTimeout) {
}
