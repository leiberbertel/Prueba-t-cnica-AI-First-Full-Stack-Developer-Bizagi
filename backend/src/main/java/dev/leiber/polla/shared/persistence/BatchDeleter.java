package dev.leiber.polla.shared.persistence;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Borra datos en lotes pequeños, cada uno en su propia transacción corta y con timeouts (ADR-0007).
 * <p>
 * Nunca hay una transacción larga: si un lote encuentra un bloqueo o tarda demasiado, falla rápido
 * ({@code lock_timeout} / {@code statement_timeout}) en lugar de acumular esperas en la base de datos.
 */
@Component
@EnableConfigurationProperties(PurgeProperties.class)
public class BatchDeleter {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final PurgeProperties properties;

    BatchDeleter(JdbcClient jdbc, PlatformTransactionManager transactionManager, PurgeProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Ejecuta {@code deleteSql} repetidamente hasta que un lote borra menos de {@code batchSize} filas.
     * La sentencia debe limitar cada lote con el parámetro {@code :limit}, idealmente con
     * {@code FOR UPDATE SKIP LOCKED} para no esperar filas bloqueadas por otras transacciones.
     *
     * @return total de filas borradas
     */
    public int deleteInBatches(String deleteSql, Map<String, ?> params) {
        int total = 0;
        int deleted;
        do {
            deleted = inShortTransaction(() -> jdbc.sql(deleteSql)
                    .params(params)
                    .param("limit", properties.batchSize())
                    .update());
            total += deleted;
        } while (deleted == properties.batchSize());
        return total;
    }

    /** Ejecuta {@code work} en una transacción nueva con los timeouts de purga aplicados solo a ella. */
    public <T> T inShortTransaction(Supplier<T> work) {
        return transaction.execute(status -> {
            // set_config(..., true) equivale a SET LOCAL: el límite muere con la transacción.
            jdbc.sql("select set_config('lock_timeout', :lock, true), set_config('statement_timeout', :stmt, true)")
                    .param("lock", properties.lockTimeout().toMillis() + "ms")
                    .param("stmt", properties.statementTimeout().toMillis() + "ms")
                    .query()
                    .singleRow();
            return work.get();
        });
    }
}
