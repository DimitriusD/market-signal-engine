package com.trading.marketsignalengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.application.port.output.SignalMetricsPort;
import com.trading.marketsignalengine.application.service.ValidatedMarketSignalEvaluator;
import com.trading.marketsignalengine.event.config.PublishTimeoutHierarchy;
import com.trading.marketsignalengine.event.metrics.MicrometerSignalMetrics;
import com.trading.marketsignalengine.event.publisher.MarketSignalSnapshotPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Boots the full Spring context with the listener stopped (no broker needed) and checks that the
 * composition root wires what the configuration says: listener properties really reach the container
 * (4.2), publish timeout is bounded (4.1), validator allowlist and signal version come from config,
 * metrics are Micrometer-backed (4.4).
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:1",
        "app.kafka.schema-registry.url=mock://context-test",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.listener.concurrency=3",
        "spring.kafka.listener.ack-mode=record",
        "spring.kafka.listener.poll-timeout=1234",
        "app.kafka.publish-timeout-ms=7000",
        "app.signal.signal-set-version=mse-signals-v8",
        "app.signal.max-spread-bps=3.5",
        "app.signal.supported-feature-set-versions=mfs-features-v2,mfs-core-v2"
})
class ApplicationContextTest {

    @Autowired
    private SignalConfiguration signalConfiguration;
    @Autowired
    private MarketFeaturesSnapshotValidator validator;
    @Autowired
    private MarketFeaturesHandler handler;
    @Autowired
    private MarketSignalSnapshotPublisherPort publisher;
    @Autowired
    private SignalMetricsPort metrics;
    @Autowired
    private ConcurrentKafkaListenerContainerFactory<String, MarketFeaturesSnapshotEvent>
            marketFeaturesKafkaListenerContainerFactory;
    @Autowired
    private KafkaListenerEndpointRegistry registry;
    @Autowired
    private PublishTimeoutHierarchy publishTimeoutHierarchy;
    @Autowired
    private ValidatedMarketSignalEvaluator evaluator;

    @Test
    void signalConfigurationComesFromProperties() {
        assertEquals("mse-signals-v8", signalConfiguration.signalSetVersion());
        assertEquals(new BigDecimal("3.5"), signalConfiguration.maxSpreadBps());
    }

    @Test
    void validatorAllowlistComesFromProperties() {
        assertEquals(Set.of("mfs-features-v2", "mfs-core-v2"), validator.supportedFeatureSetVersions());
    }

    @Test
    void publisherIsBoundedByConfiguredTimeout() {
        MarketSignalSnapshotPublisher p = assertInstanceOf(MarketSignalSnapshotPublisher.class, publisher);
        assertEquals(Duration.ofMillis(7000), p.publishTimeout());
        assertEquals("state.market.signals.v1", p.topic());
        // request (3000) < delivery (5000) from application.yml < publish (7000) from this test
        assertEquals(new PublishTimeoutHierarchy(3000L, 5000L, 7000L), publishTimeoutHierarchy);
    }

    @Test
    void liveHandlerUsesTheSharedValidatedEvaluator() {
        assertNotNull(evaluator);
        assertEquals(Set.of("mfs-features-v2", "mfs-core-v2"), evaluator.validator().supportedFeatureSetVersions());
    }

    @Test
    void metricsAreMicrometerBacked() {
        assertInstanceOf(MicrometerSignalMetrics.class, metrics);
        assertNotNull(handler);
    }

    @Test
    void listenerPropertiesReachTheContainer() {
        // spring.kafka.listener.* must be honoured by the factory (Boot configurer), not ignored.
        assertEquals(3, marketFeaturesKafkaListenerContainerFactory.getContainerProperties() == null
                ? -1 : concurrencyOf());
        assertFalse(registry.getListenerContainers().isEmpty(), "listener must be registered");
        MessageListenerContainer container = registry.getListenerContainers().iterator().next();
        assertFalse(container.isRunning(), "auto-startup=false must keep the listener stopped");
        assertFalse(container.isAutoStartup());
        ContainerProperties props = container.getContainerProperties();
        assertEquals(ContainerProperties.AckMode.RECORD, props.getAckMode());
        assertEquals(1234L, props.getPollTimeout());
        assertTrue(props.getTopics() != null && props.getTopics().length == 1);
        assertEquals("market.feature.snapshot.v1", props.getTopics()[0]);
    }

    private int concurrencyOf() {
        MessageListenerContainer container = registry.getListenerContainers().iterator().next();
        return ((org.springframework.kafka.listener.ConcurrentMessageListenerContainer<?, ?>) container).getConcurrency();
    }
}
