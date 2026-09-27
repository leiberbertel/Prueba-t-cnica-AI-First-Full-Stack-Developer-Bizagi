package dev.leiber.polla.demo;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea participantes demo con predicciones para que el ranking tenga vida desde el primer minuto.
 * Idempotente: si los usuarios ya existen, no hace nada. Nunca crea resultados: eso lo hace el admin en vivo.
 */
@Component
@Order(100)
@ConditionalOnBooleanProperty("app.demo.enabled")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final List<String[]> PARTICIPANTS = List.of(
            new String[] { "ana@polla.local", "Ana" },
            new String[] { "carlos@polla.local", "Carlos" },
            new String[] { "valentina@polla.local", "Valentina" },
            new String[] { "diego@polla.local", "Diego" });

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String password;

    DemoDataSeeder(JdbcClient jdbc, PasswordEncoder passwordEncoder, Clock clock,
            @Value("${app.demo.password:Usuario123}") String password) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var now = clock.instant().atOffset(ZoneOffset.UTC);
        List<Long> matchIds = jdbc.sql("select id from matches order by kickoff_at, id").query(Long.class).list();
        var hash = passwordEncoder.encode(password);
        var random = new Random(2026); // determinista: misma demo en cada entorno
        int created = 0;
        for (String[] participant : PARTICIPANTS) {
            boolean exists = jdbc.sql("select count(*) from users where email = :email")
                    .param("email", participant[0]).query(Integer.class).single() > 0;
            if (exists) {
                continue;
            }
            long userId = jdbc.sql("""
                    insert into users (email, display_name, password_hash, role, created_at)
                    values (:email, :name, :hash, 'USER', :now) returning id
                    """)
                    .param("email", participant[0]).param("name", participant[1]).param("hash", hash)
                    .param("now", now)
                    .query(Long.class).single();
            // Cada participante deja sin predecir algunos partidos, para que el dashboard muestre pendientes.
            for (long matchId : matchIds.subList(0, matchIds.size() - created - 1)) {
                jdbc.sql("""
                        insert into predictions (user_id, match_id, home_goals, away_goals, created_at, updated_at)
                        values (:user, :match, :home, :away, :now, :now)
                        """)
                        .param("user", userId).param("match", matchId)
                        .param("home", random.nextInt(4)).param("away", random.nextInt(3))
                        .param("now", now)
                        .update();
            }
            created++;
        }
        if (created > 0) {
            log.info("Datos demo: {} participantes creados", created);
        }
    }
}
