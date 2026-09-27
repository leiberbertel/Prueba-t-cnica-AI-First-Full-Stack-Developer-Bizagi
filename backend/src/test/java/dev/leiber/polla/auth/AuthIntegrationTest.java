package dev.leiber.polla.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import dev.leiber.polla.IntegrationTest;
import jakarta.servlet.http.Cookie;

/** specs/01-auth/spec.md */
class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerCreatesUserAndReturnsTokens() throws Exception { // CA-01.1, CA-01.6
        mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"  Ana@Example.com ","displayName":"Ana","password":"Secreta123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("ana@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refresh_token="),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict"),
                        org.hamcrest.Matchers.containsString("Path=/api/v1/auth"))));
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception { // CA-01.2
        mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"dup@example.com","displayName":"Uno","password":"Secreta123"}"""))
                .andExpect(status().isCreated());

        mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"DUP@example.com","displayName":"Dos","password":"Secreta123"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:polla:problem:email-taken"));
    }

    @Test
    void weakPasswordReturnsFieldErrors() throws Exception { // CA-01.3
        mvc.perform(json(post("/api/v1/auth/register"), """
                {"email":"weak@example.com","displayName":"Weak","password":"solotexto"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void loginErrorsDoNotRevealWhetherEmailExists() throws Exception { // CA-01.5
        var user = register("Luis");
        String email = jdbc.sql("select email from users where id = :id").param("id", user.userId())
                .query(String.class).single();

        var wrongPassword = mvc.perform(json(post("/api/v1/auth/login"), """
                {"email":"%s","password":"Incorrecta1"}""".formatted(email)).with(uniqueIp()))
                .andExpect(status().isUnauthorized()).andReturn();
        var unknownEmail = mvc.perform(json(post("/api/v1/auth/login"), """
                {"email":"nadie@example.com","password":"Incorrecta1"}""").with(uniqueIp()))
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
    }

    @Test
    void adminAccountIsBootstrappedFromConfiguration() throws Exception { // CA-01.11
        var admin = loginAdmin();

        mvc.perform(get("/api/v1/me").with(admin.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() throws Exception { // CA-01.7, CA-01.8
        var session = register("Rotacion");

        var refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie rotated = refreshed.getResponse().getCookie("refresh_token");
        assertThat(rotated.getValue()).isNotEqualTo(session.refreshCookie().getValue());

        // Reutilizar el token viejo fuera del período de gracia = posible robo → se revocan todas las sesiones.
        clock.set(clock.instant().plus(Duration.ofSeconds(30)));
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieMeansNoSessionNotAnError() throws Exception { // CA-01.7
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "no-existe")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception { // CA-01.9
        var session = register("Salida");

        mvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsRateLimitedPerIp() throws Exception { // CA-01.10
        for (int attempt = 1; attempt <= 10; attempt++) {
            mvc.perform(json(post("/api/v1/auth/login"), """
                    {"email":"x@example.com","password":"Incorrecta1"}""").with(request -> {
                request.setRemoteAddr("192.168.99.99");
                return request;
            })).andExpect(status().isUnauthorized());
        }

        mvc.perform(json(post("/api/v1/auth/login"), """
                {"email":"x@example.com","password":"Incorrecta1"}""").with(request -> {
            request.setRemoteAddr("192.168.99.99");
            return request;
        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception { // CA-01.12
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:polla:problem:unauthorized"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer token.invalido.x"))
                .andExpect(status().isUnauthorized());
    }
}
