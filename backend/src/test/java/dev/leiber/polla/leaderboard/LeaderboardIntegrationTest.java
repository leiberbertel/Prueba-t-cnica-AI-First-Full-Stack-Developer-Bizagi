package dev.leiber.polla.leaderboard;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.leiber.polla.IntegrationTest;

/** specs/04-leaderboard/spec.md */
class LeaderboardIntegrationTest extends IntegrationTest {

    @Test
    void ranksByPointsThenExactHitsWithSharedPositions() throws Exception { // CA-04.1, CA-04.2, RN-07, RN-09
        var admin = loginAdmin();
        List<Long> ids = matchIds();
        var ana = register("Ana");     // 1 exacto            = 3 pts, 1 exacto
        var beto = register("Beto");   // 3 aciertos ganador  = 3 pts, 0 exactos
        var caro = register("Caro");   // igual que Ana       = 3 pts, 1 exacto → empata con Ana
        register("Dani");              // sin predicciones    = 0 pts

        predict(ana, ids.get(0), 2, 1);
        predict(caro, ids.get(0), 2, 1);
        predict(beto, ids.get(0), 1, 0);
        predict(beto, ids.get(1), 1, 0);
        predict(beto, ids.get(2), 1, 0);
        registerResult(admin, ids.get(0), 2, 1);
        registerResult(admin, ids.get(1), 3, 1);
        registerResult(admin, ids.get(2), 2, 0);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> mvc.perform(get("/api/v1/leaderboard")
                .with(ana.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4)) // el admin no aparece
                .andExpect(jsonPath("$[0].displayName").value("Ana"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[1].displayName").value("Caro"))
                .andExpect(jsonPath("$[1].position").value(1))
                .andExpect(jsonPath("$[2].displayName").value("Beto"))
                .andExpect(jsonPath("$[2].position").value(2))
                .andExpect(jsonPath("$[2].outcomeHits").value(3))
                .andExpect(jsonPath("$[3].displayName").value("Dani"))
                .andExpect(jsonPath("$[3].points").value(0)));
    }

    @Test
    void othersOnlySeePredictionsOfClosedMatches() throws Exception { // CA-04.3, CA-04.4, RN-06
        var admin = loginAdmin();
        List<Long> ids = matchIds();
        var owner = register("Duena");
        var curious = register("Curioso");
        predict(owner, ids.get(0), 1, 0);
        predict(owner, ids.get(1), 2, 2);
        registerResult(admin, ids.get(0), 1, 0);

        mvc.perform(get("/api/v1/users/{id}/predictions", owner.userId()).with(curious.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("Duena"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].matchId").value(ids.get(0)));

        mvc.perform(get("/api/v1/users/{id}/predictions", owner.userId()).with(owner.auth()))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void summaryShowsPendingPredictions() throws Exception { // CA-04.7
        var user = register("Resumen");
        predict(user, matchIds().getFirst(), 1, 0);

        mvc.perform(get("/api/v1/me/summary").with(user.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingPredictions").value(11))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.totalParticipants").value(1));
    }

    @Test
    void unknownUserHistoryReturns404() throws Exception { // CA-04.5
        var user = register("Buscador");

        mvc.perform(get("/api/v1/users/{id}/predictions", 999_999).with(user.auth()))
                .andExpect(status().isNotFound());
    }
}
