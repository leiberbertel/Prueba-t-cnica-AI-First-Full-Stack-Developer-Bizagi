package dev.leiber.polla;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Base de pruebas de integración: app completa + Postgres real (Testcontainers) + reloj controlable.
 * Cada prueba parte de un estado limpio: 12 partidos sin resultado y solo el admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, IntegrationTest.ClockConfig.class })
public abstract class IntegrationTest {

    /** Antes del primer partido (2026-11-20): todas las predicciones abiertas. */
    protected static final Instant BEFORE_TOURNAMENT = Instant.parse("2026-11-01T12:00:00Z");
    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "Admin12345";
    protected static final String PASSWORD = "Secreta123";

    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(1);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetState() {
        clock.set(BEFORE_TOURNAMENT);
        jdbc.sql("delete from predictions").update();
        jdbc.sql("delete from refresh_tokens").update();
        jdbc.sql("delete from users where role = 'USER'").update();
        jdbc.sql("""
                update matches set status = 'SCHEDULED', home_goals = null, away_goals = null,
                                   result_registered_at = null
                """).update();
    }

    protected Session register(String displayName) throws Exception {
        var email = displayName.toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        var result = mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"%s","displayName":"%s","password":"%s"}""".formatted(email, displayName, PASSWORD)))
                .andReturn();
        return session(result);
    }

    protected Session loginAdmin() throws Exception {
        var result = mvc.perform(json(post("/api/v1/auth/login"), """
                {"email":"%s","password":"%s"}""".formatted(ADMIN_EMAIL, ADMIN_PASSWORD)).with(uniqueIp()))
                .andReturn();
        return session(result);
    }

    protected List<Long> matchIds() {
        return jdbc.sql("select id from matches order by kickoff_at, id").query(Long.class).list();
    }

    protected Instant kickoffOf(long matchId) {
        return jdbc.sql("select kickoff_at from matches where id = :id").param("id", matchId)
                .query(java.time.OffsetDateTime.class).single().toInstant();
    }

    protected MvcResult predict(Session session, long matchId, int home, int away) throws Exception {
        return mvc.perform(json(put("/api/v1/matches/{id}/prediction", matchId), """
                {"homeGoals":%d,"awayGoals":%d}""".formatted(home, away)).with(session.auth())).andReturn();
    }

    protected MvcResult registerResult(Session admin, long matchId, int home, int away) throws Exception {
        return mvc.perform(json(put("/api/v1/admin/matches/{id}/result", matchId), """
                {"homeGoals":%d,"awayGoals":%d}""".formatted(home, away)).with(admin.auth())).andReturn();
    }

    protected Integer pointsOf(long userId, long matchId) {
        return jdbc.sql("select points from predictions where user_id = :u and match_id = :m")
                .param("u", userId).param("m", matchId)
                .query((rs, row) -> (Integer) rs.getObject("points", Integer.class))
                .single();
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Cada login usa una IP distinta para no chocar con el rate limit entre pruebas. */
    protected static RequestPostProcessor uniqueIp() {
        int n = IP_SEQUENCE.getAndIncrement();
        return request -> {
            request.setRemoteAddr("10.1.%d.%d".formatted(n / 250, n % 250 + 1));
            return request;
        };
    }

    private static Session session(MvcResult result) throws Exception {
        var body = result.getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.user.id");
        return new Session(id.longValue(), JsonPath.read(body, "$.accessToken"),
                result.getResponse().getCookie("refresh_token"));
    }

    public record Session(long userId, String accessToken, Cookie refreshCookie) {

        public RequestPostProcessor auth() {
            return request -> {
                request.addHeader("Authorization", "Bearer " + accessToken);
                return request;
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(BEFORE_TOURNAMENT);
        }
    }
}
