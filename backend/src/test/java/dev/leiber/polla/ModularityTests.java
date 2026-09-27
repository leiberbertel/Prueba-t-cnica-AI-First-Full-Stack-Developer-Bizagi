package dev.leiber.polla;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Falla el build si un módulo usa los internos de otro o si hay dependencias cíclicas (ADR-0002).
 * Además genera diagramas C4 de componentes en target/spring-modulith-docs.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(PollaApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void writesDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
