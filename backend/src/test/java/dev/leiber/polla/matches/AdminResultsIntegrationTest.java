package dev.leiber.polla.matches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.leiber.polla.IntegrationTest;

/** specs/03-admin-results/spec.md + recálculo de puntos (RN-05, ADR-0003) */
class AdminResultsIntegrationTest extends IntegrationTest {

    @Test
    void regularUserCannotRegisterResults() throws Exception { // CA-03.3
        var user = register("Intruso");

        assertThat(registerResult(user, matchIds().getFirst(), 1, 0).getResponse().getStatus()).isEqualTo(403);
        mvc.perform(get("/api/v1/admin/matches").with(user.auth())).andExpect(status().isForbidden());
    }

    @Test
    void adminListsMatchesWithPredictionCounts() throws Exception { // HU-03.3
        var admin = loginAdmin();
        long first = matchIds().getFirst();
        predict(register("Uno"), first, 1, 0);
        predict(register("Dos"), first, 0, 0);

        mvc.perform(get("/api/v1/admin/matches").with(admin.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predictionsCount").value(2))
                .andExpect(jsonPath("$[1].predictionsCount").value(0));
    }

    @Test
    void registeringResultScoresEveryPrediction() throws Exception { // CA-03.2, CA-03.4, RN-04
        var admin = loginAdmin();
        long matchId = matchIds().getFirst();
        var exact = register("Exacto");
        var outcome = register("Ganador");
        var miss = register("Fallo");
        predict(exact, matchId, 2, 1);
        predict(outcome, matchId, 3, 0);
        predict(miss, matchId, 0, 1);

        mvc.perform(put("/api/v1/admin/matches/{id}/result", matchId).with(admin.auth())
                .contentType("application/json").content("{\"homeGoals\":2,\"awayGoals\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.result.homeGoals").value(2))
                .andExpect(jsonPath("$.predictionsCount").value(3));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(pointsOf(exact.userId(), matchId)).isEqualTo(3);
            assertThat(pointsOf(outcome.userId(), matchId)).isEqualTo(1);
            assertThat(pointsOf(miss.userId(), matchId)).isEqualTo(0);
        });
    }

    @Test
    void correctingResultRecalculatesIdempotently() throws Exception { // HU-03.2, RN-05
        var admin = loginAdmin();
        long matchId = matchIds().getFirst();
        var user = register("Correccion");
        predict(user, matchId, 1, 1);

        registerResult(admin, matchId, 2, 1);
        await().atMost(Duration.ofSeconds(5)).until(() -> Integer.valueOf(0).equals(pointsOf(user.userId(), matchId)));

        registerResult(admin, matchId, 1, 1);
        registerResult(admin, matchId, 1, 1);
        await().atMost(Duration.ofSeconds(5)).until(() -> Integer.valueOf(3).equals(pointsOf(user.userId(), matchId)));

        mvc.perform(get("/api/v1/leaderboard").with(user.auth()))
                .andExpect(jsonPath("$[0].points").value(3)); // corregido, no acumulado
    }

    @Test
    void staleVersionIsRejected() throws Exception { // CA-03.7
        var admin = loginAdmin();
        long matchId = matchIds().getFirst();
        long version = jdbc.sql("select version from matches where id = :id").param("id", matchId)
                .query(Long.class).single();
        registerResult(admin, matchId, 1, 0); // otro admin guardó primero

        mvc.perform(put("/api/v1/admin/matches/{id}/result", matchId).with(admin.auth())
                .contentType("application/json")
                .content("{\"homeGoals\":2,\"awayGoals\":0,\"version\":%d}".formatted(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:polla:problem:stale-version"));
    }
}
