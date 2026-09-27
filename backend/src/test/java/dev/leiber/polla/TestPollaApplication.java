package dev.leiber.polla;

import org.springframework.boot.SpringApplication;

/**
 * Arranca la app en modo desarrollo con Postgres en Testcontainers: {@code ./mvnw spring-boot:test-run}.
 * No requiere instalar ni configurar una base de datos.
 */
public class TestPollaApplication {

    public static void main(String[] args) {
        SpringApplication.from(PollaApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("local")
                .run(args);
    }
}
