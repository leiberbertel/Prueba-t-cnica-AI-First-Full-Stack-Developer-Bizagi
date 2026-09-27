package dev.leiber.polla;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * Prueba de contrato (ADR-0005): cada operación de specs/api/openapi.yaml debe existir en el backend y viceversa.
 * Si alguien agrega un endpoint sin actualizar la spec (o al revés), el build falla.
 */
class ApiContractTest extends IntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void implementedEndpointsMatchTheContract() throws IOException {
        assertThat(implementedOperations()).isEqualTo(contractOperations());
    }

    @SuppressWarnings("unchecked")
    private static Set<String> contractOperations() throws IOException {
        Map<String, Object> spec;
        try (InputStream in = Files.newInputStream(Path.of("..", "specs", "api", "openapi.yaml"))) {
            spec = new Yaml().load(in);
        }
        var basePath = (String) ((Map<String, Object>) ((java.util.List<?>) spec.get("servers")).getFirst()).get("url");
        var operations = new TreeSet<String>();
        ((Map<String, Map<String, Object>>) spec.get("paths")).forEach((path, item) -> item.keySet().stream()
                .filter(HTTP_METHODS::contains)
                .forEach(method -> operations.add(method.toUpperCase() + " " + basePath + path)));
        return operations;
    }

    private Set<String> implementedOperations() {
        var operations = new TreeSet<String>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
                .filter(pattern -> pattern.startsWith("/api/"))
                .forEach(pattern -> info.getMethodsCondition().getMethods()
                        .forEach(method -> operations.add(method.name() + " " + pattern))));
        return operations;
    }
}
