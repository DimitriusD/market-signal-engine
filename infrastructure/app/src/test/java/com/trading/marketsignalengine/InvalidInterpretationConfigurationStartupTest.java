package com.trading.marketsignalengine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Invalid or missing interpretation configuration must break application startup with an actionable
 * message rather than run with fabricated policy values: a missing config hash (deliberately no
 * default in application.yml), a placeholder config hash, and validity values the domain policy
 * rejects. A fully explicit configuration starts.
 */
class InvalidInterpretationConfigurationStartupTest {

    private static final Map<String, Object> BASE = Map.of(
            "spring.kafka.bootstrap-servers", "localhost:1",
            "app.kafka.schema-registry.url", "mock://invalid-interpretation-test",
            "spring.kafka.listener.auto-startup", "false",
            "spring.main.banner-mode", "off");

    @Test
    void missingConfigHashFailsStartup() {
        // application.yml deliberately has no default for APP_INTERPRETATION_CONFIG_HASH
        Throwable root = rootOf(assertThrows(Throwable.class, () -> run(Map.of()).close()));
        assertTrue(root.getMessage() != null && root.getMessage().contains("APP_INTERPRETATION_CONFIG_HASH"),
                String.valueOf(root.getMessage()));
    }

    @Test
    void placeholderConfigHashFailsStartup() {
        Throwable root = rootOf(assertThrows(Throwable.class,
                () -> run(Map.of("app.interpretation.config-hash", "unknown")).close()));
        assertTrue(root.getMessage() != null && root.getMessage().contains("placeholder"),
                String.valueOf(root.getMessage()));
    }

    @Test
    void invalidValidityConfigurationFailsStartup() {
        // a negative adjustment violates the domain validity-policy invariants
        Throwable negative = rootOf(assertThrows(Throwable.class, () -> run(Map.of(
                "app.interpretation.config-hash", "cfg-test-1",
                "app.interpretation.validity.publication-safety-buffer-ms", "-1")).close()));
        assertTrue(negative.getMessage() != null && negative.getMessage().contains("publicationSafetyBuffer"),
                String.valueOf(negative.getMessage()));

        // a base validity that does not exceed the publication buffer can produce validUntil <= evaluatedAt
        Throwable tooSmall = rootOf(assertThrows(Throwable.class, () -> run(Map.of(
                "app.interpretation.config-hash", "cfg-test-1",
                "app.interpretation.validity.no-opportunity-base-validity-ms", "100")).close()));
        assertTrue(tooSmall.getMessage() != null
                        && tooSmall.getMessage().contains("noOpportunityBaseValidity"),
                String.valueOf(tooSmall.getMessage()));
    }

    @Test
    void explicitConfigurationStartsTheContext() {
        try (ConfigurableApplicationContext context =
                     run(Map.of("app.interpretation.config-hash", "cfg-test-1"))) {
            assertNotNull(context.getBean(
                    com.trading.marketsignalengine.application.service.ValidatedMarketInterpretationEvaluator.class));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Throwable rootOf(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /** Overrides go in as command-line arguments (highest precedence over application.yml). */
    private static ConfigurableApplicationContext run(Map<String, Object> overrides) {
        Map<String, Object> props = new HashMap<>(BASE);
        props.putAll(overrides);
        String[] args = props.entrySet().stream()
                .map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run(args);
    }
}
