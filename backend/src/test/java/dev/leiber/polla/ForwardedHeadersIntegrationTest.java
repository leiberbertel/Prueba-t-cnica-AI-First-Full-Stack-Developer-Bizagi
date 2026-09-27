package dev.leiber.polla;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * El rate limit de login usa la IP real del cliente, no la que el cliente escribe en {@code X-Forwarded-For}.
 * <p>
 * Necesita un servidor real: el {@code RemoteIpValve} de Tomcat (strategy {@code native}) no participa en MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, IntegrationTest.ClockConfig.class })
class ForwardedHeadersIntegrationTest {

    private static final String LOGIN_BODY = "{\"email\":\"nadie@example.com\",\"password\":\"Incorrecta1\"}";

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void spoofedForwardedForCannotBypassLoginRateLimit() throws Exception { // CA-01.10
        // El atacante cambia la IP falsa (izquierda) en cada intento; el proxy de borde agrega la real (derecha).
        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThat(login("198.51.100." + attempt + ", 203.0.113.7")).isEqualTo(401);
        }

        assertThat(login("198.51.100.99, 203.0.113.7")).isEqualTo(429);
    }

    @Test
    void differentRealClientsAreLimitedIndependently() throws Exception {
        for (int attempt = 1; attempt <= 10; attempt++) {
            login("203.0.113.20");
        }
        assertThat(login("203.0.113.20")).isEqualTo(429);

        assertThat(login("203.0.113.21")).isEqualTo(401);
    }

    private int login(String forwardedFor) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(LOGIN_BODY))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
