package dev.leiber.polla.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import javax.sql.DataSource;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import dev.leiber.polla.IntegrationTest;
import dev.leiber.polla.auth.application.AccountPurger;
import dev.leiber.polla.auth.application.RefreshTokenJanitor;

/** HU-01.6 · Eliminar mi cuenta, en dos fases (specs/01-auth/spec.md, ADR-0007, RN-10). */
@RecordApplicationEvents
class AccountDeletionIntegrationTest extends IntegrationTest {

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private AccountPurger accountPurger;

    @Autowired
    private RefreshTokenJanitor refreshTokenJanitor;

    @Autowired
    private DataSource dataSource;

    @Test
    void deletionAnonymizesImmediatelyAndPurgesInBackground() throws Exception { // CA-01.13
        var user = register("Borrame");
        String email = emailOf(user.userId());
        List<Long> ids = matchIds();
        for (int i = 0; i < 5; i++) { // 5 predicciones con lotes de 2 → varias transacciones
            predict(user, ids.get(i), 1, 0);
        }

        mvc.perform(json(delete("/api/v1/me"), """
                {"password":"%s"}""".formatted(PASSWORD)).with(user.auth()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Set-Cookie", Matchers.containsString("Max-Age=0")));

        // Fase síncrona: datos personales anonimizados y sesiones cerradas al instante.
        var row = jdbc.sql("select email, display_name, deleted_at from users where id = :id")
                .param("id", user.userId()).query().singleRow();
        assertThat(row.get("email")).isEqualTo("deleted+" + user.userId() + "@polla.invalid");
        assertThat(row.get("display_name")).isEqualTo("Cuenta eliminada");
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(events.stream(UserAccountDeleted.class)).containsExactly(new UserAccountDeleted(user.userId()));
        mvc.perform(json(post("/api/v1/auth/login"), """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)).with(uniqueIp()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()))
                .andExpect(status().isUnauthorized());
        var other = register("Queda");
        mvc.perform(get("/api/v1/leaderboard").with(other.auth()))
                .andExpect(jsonPath("$[*].userId", Matchers.not(Matchers.hasItem((int) user.userId()))));
        mvc.perform(get("/api/v1/users/{id}/predictions", user.userId()).with(other.auth()))
                .andExpect(status().isNotFound());

        // Fase asíncrona: cada módulo purga sus datos por lotes y luego se borra la fila.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(count("predictions", user.userId())).isZero();
            assertThat(count("refresh_tokens", user.userId())).isZero();
        });
        assertThat(accountPurger.finalizePendingDeletions()).isEqualTo(1);
        assertThat(count("users", user.userId())).isZero();
    }

    @Test
    void userRowCannotBeRemovedWhileDataRemains() { // ADR-0007: FK RESTRICT
        long userId = insertDeletedUserWithPrediction();

        assertThat(accountPurger.finalizePendingDeletions()).isZero();
        assertThat(count("users", userId)).isOne();
    }

    @Test
    void lockedRowsFailFastInsteadOfBlocking() throws Exception { // CA-01.18
        long userId = insertDeletedUserWithPrediction();
        jdbc.sql("delete from predictions where user_id = :id").param("id", userId).update();

        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("select id from users where id = ? for update")) {
                lock.setLong(1, userId);
                lock.executeQuery();
            }

            long start = System.nanoTime();
            int finalized = accountPurger.finalizePendingDeletions();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(finalized).isZero();
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5)); // lock_timeout = 2 s: no espera indefinidamente
            blocker.rollback();
        }

        assertThat(accountPurger.finalizePendingDeletions()).isEqualTo(1); // al liberarse el bloqueo, se completa
        assertThat(count("users", userId)).isZero();
    }

    @Test
    void wrongPasswordKeepsTheAccount() throws Exception { // CA-01.14
        var user = register("Cuidadoso");

        mvc.perform(json(delete("/api/v1/me"), """
                {"password":"Incorrecta9"}""").with(user.auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:polla:problem:invalid-password"));

        assertThat(jdbc.sql("select deleted_at from users where id = :id").param("id", user.userId())
                .query(Instant.class).optional()).isEmpty();
        assertThat(events.stream(UserAccountDeleted.class)).isEmpty();
    }

    @Test
    void adminCannotDeleteTheirAccount() throws Exception { // CA-01.15, RN-10
        var admin = loginAdmin();

        mvc.perform(json(delete("/api/v1/me"), """
                {"password":"%s"}""".formatted(ADMIN_PASSWORD)).with(admin.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:polla:problem:admin-cannot-be-deleted"));

        assertThat(count("users", admin.userId())).isOne();
    }

    @Test
    void sameEmailCanRegisterAgainImmediately() throws Exception { // CA-01.16
        var user = register("Regresa");
        String email = emailOf(user.userId());
        mvc.perform(json(delete("/api/v1/me"), """
                {"password":"%s"}""".formatted(PASSWORD)).with(user.auth()))
                .andExpect(status().isAccepted());

        mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"%s","displayName":"Regresa","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    @Test
    void tokenIssuedBeforeDeletionCannotWrite() throws Exception { // CA-01.17
        var user = register("Fantasma");
        mvc.perform(json(delete("/api/v1/me"), """
                {"password":"%s"}""".formatted(PASSWORD)).with(user.auth()))
                .andExpect(status().isAccepted());

        assertThat(predict(user, matchIds().getFirst(), 1, 1).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void expiredRefreshTokensArePurged() throws Exception { // CA-01.19
        var user = register("Antiguo");
        jdbc.sql("""
                insert into refresh_tokens (user_id, token_hash, expires_at, created_at)
                values (:u, :h, :exp, :exp)
                """).param("u", user.userId()).param("h", "a".repeat(64))
                .param("exp", clock.instant().minus(Duration.ofDays(10)).atOffset(ZoneOffset.UTC)).update();

        assertThat(refreshTokenJanitor.purgeExpired()).isEqualTo(1);
        assertThat(count("refresh_tokens", user.userId())).isOne(); // la sesión vigente se conserva
    }

    @Test
    void requiresAuthenticationAndPassword() throws Exception { // CA-01.12, validación
        mvc.perform(json(delete("/api/v1/me"), """
                {"password":"x"}"""))
                .andExpect(status().isUnauthorized());

        var user = register("SinClave");
        mvc.perform(json(delete("/api/v1/me"), "{}").with(user.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    private long insertDeletedUserWithPrediction() {
        long userId = jdbc.sql("""
                insert into users (email, display_name, password_hash, role, created_at, deleted_at)
                values (:email, 'Cuenta eliminada', '!', 'USER', now(), now()) returning id
                """).param("email", "deleted+" + System.nanoTime() + "@polla.invalid").query(Long.class).single();
        jdbc.sql("insert into predictions (user_id, match_id, home_goals, away_goals) values (:u, :m, 1, 0)")
                .param("u", userId).param("m", matchIds().getFirst()).update();
        return userId;
    }

    private String emailOf(long userId) {
        return jdbc.sql("select email from users where id = :id").param("id", userId).query(String.class).single();
    }

    private int count(String table, long userId) {
        String column = "users".equals(table) ? "id" : "user_id";
        return jdbc.sql("select count(*) from " + table + " where " + column + " = :id")
                .param("id", userId).query(Integer.class).single();
    }
}
