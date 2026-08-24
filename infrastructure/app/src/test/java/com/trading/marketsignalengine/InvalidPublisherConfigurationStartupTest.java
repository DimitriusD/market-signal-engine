package com.trading.marketsignalengine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Invalid publisher configuration must break application startup with a clear, actionable message
 * rather than surface later as a runtime publish failure: blank output topic, non-positive publish
 * timeout, and a broken timeout hierarchy (publish wait not strictly above the producer's
 * delivery.timeout.ms, or delivery not strictly above request).
 */
class InvalidPublisherConfigurationStartupTest {

    private static final Map<String, Object> BASE = Map.of(
            "spring.kafka.bootstrap-servers", "localhost:1",
            "app.kafka.schema-registry.url", "mock://invalid-config-test",
            "spring.kafka.listener.auto-startup", "false",
            "spring.main.banner-mode", "off",
            "app.interpretation.config-hash", "cfg-invalid-config-test-1");

    @Test
    void blankOutputTopicFailsStartup() {
        Throwable root = assertStartupFails(Map.of("app.kafka.topic.market-signals", " "));

        assertTrue(root.getMessage().contains("app.kafka.topic.market-signals"), root.getMessage());
    }

    @Test
    void nonPositivePublishTimeoutFailsStartup() {
        Throwable root = assertStartupFails(Map.of("app.kafka.publish-timeout-ms", "0"));

        assertTrue(root.getMessage().contains("app.kafka.publish-timeout-ms"), root.getMessage());
    }

    @Test
    void publishTimeoutNotAboveDeliveryTimeoutFailsStartup() {
        // application.yml: request 3000 < delivery 5000; publish must be > 5000
        Throwable root = assertStartupFails(Map.of("app.kafka.publish-timeout-ms", "5000"));

        assertTrue(root.getMessage().contains("delivery.timeout.ms=5000"), root.getMessage());
        assertTrue(root.getMessage().contains("publish-timeout-ms=5000"), root.getMessage());
    }

    @Test
    void requestTimeoutNotBelowDeliveryTimeoutFailsStartup() {
        Throwable root = assertStartupFails(Map.of(
                "spring.kafka.producer.properties.request.timeout.ms", "5000",
                "spring.kafka.producer.properties.delivery.timeout.ms", "5000",
                "app.kafka.publish-timeout-ms", "6500"));

        assertTrue(root.getMessage().contains("request.timeout.ms=5000"), root.getMessage());
    }

    @Test
    void validHierarchyStartsAndExposesTheValidatedValues() {
        try (ConfigurableApplicationContext context = run(Map.of(
                "spring.kafka.producer.properties.request.timeout.ms", "1000",
                "spring.kafka.producer.properties.delivery.timeout.ms", "2000",
                "app.kafka.publish-timeout-ms", "2001"))) {
            var hierarchy = context.getBean(com.trading.marketsignalengine.event.config.PublishTimeoutHierarchy.class);
            assertNotNull(hierarchy);
            assertTrue(hierarchy.requestTimeoutMs() < hierarchy.deliveryTimeoutMs());
            assertTrue(hierarchy.deliveryTimeoutMs() < hierarchy.publishTimeoutMs());
        }
    }

    private static Throwable assertStartupFails(Map<String, Object> overrides) {
        Throwable failure = assertThrows(Throwable.class, () -> {
            try (ConfigurableApplicationContext ignored = run(overrides)) {
                // context must not start
            }
        });
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        assertTrue(root instanceof IllegalStateException,
                "expected IllegalStateException at the root, got " + root.getClass() + ": " + root.getMessage());
        return root;
    }

    /**
     * Overrides are passed as command-line arguments (highest precedence) — {@code builder.properties()}
     * would only set <em>default</em> properties, which application.yml overrides.
     */
    private static ConfigurableApplicationContext run(Map<String, Object> overrides) {
        Map<String, Object> props = new java.util.HashMap<>(BASE);
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
