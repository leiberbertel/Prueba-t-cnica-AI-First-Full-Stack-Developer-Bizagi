package dev.leiber.polla.predictions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import dev.leiber.polla.IntegrationTest;

/** specs/02-predictions/spec.md */
class PredictionIntegrationTest extends IntegrationTest {

    @Test
    void listsTwelveSeededMatchesOpenForPredictions() throws Exception { // CA-02.1, CA-02.2
        var user = register("Lista");

        mvc.perform(get("/api/v1/matches").with(user.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].homeTeam.code").value("COL"))
                .andExpect(jsonPath("$[0].predictionOpen").value(true))
                .andExpect(jsonPath("$[0].myPrediction").isEmpty());
    }

    @Test
    void upsertCreatesThenUpdatesSinglePrediction() throws Exception { // CA-02.3, RN-01
        var user = register("Upsert");
        long matchId = matchIds().getFirst();

        assertThat(predict(user, matchId, 1, 0).getResponse().getStatus()).isEqualTo(200);
        assertThat(predict(user, matchId, 2, 2).getResponse().getStatus()).isEqualTo(200);

        mvc.perform(get("/api/v1/matches").with(user.auth()))
                .andExpect(jsonPath("$[0].myPrediction.homeGoals").value(2))
                .andExpect(jsonPath("$[0].myPrediction.awayGoals").value(2))
                .andExpect(jsonPath("$[0].myPrediction.points").isEmpty());
        assertThat(jdbc.sql("select count(*) from predictions where user_id = :u").param("u", user.userId())
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void rejectsPredictionAfterKickoff() throws Exception { // CA-02.4, RN-02
        var user = register("Tarde");
        long matchId = matchIds().getFirst();
        clock.set(kickoffOf(matchId));

        var result = predict(user, matchId, 1, 0);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("urn:polla:problem:prediction-closed");
    }

    @Test
    void rejectsPredictionForFinishedMatch() throws Exception { // RN-02
        var user = register("Cerrado");
        var admin = loginAdmin();
        long matchId = matchIds().getFirst();
        registerResult(admin, matchId, 1, 0);

        assertThat(predict(user, matchId, 1, 0).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void validatesGoalRange() throws Exception { // CA-02.5, RN-03
        var user = register("Rango");

        var result = predict(user, matchIds().getFirst(), 21, -1);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("homeGoals", "awayGoals");
    }

    @Test
    void unknownMatchReturns404() throws Exception { // CA-02.6
        var user = register("Nada");

        assertThat(predict(user, 999_999, 1, 0).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void adminCannotPredict() throws Exception { // CA-02.9, RN-09
        var admin = loginAdmin();

        assertThat(predict(admin, matchIds().getFirst(), 1, 0).getResponse().getStatus()).isEqualTo(403);
    }
}
