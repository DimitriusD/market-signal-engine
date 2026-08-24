package com.trading.marketsignalengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.contracts.signal.MarketInterpretationSnapshotEvent;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationAssemblyPolicy;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationSnapshotPublisherPort;
import com.trading.marketsignalengine.application.service.MarketInterpretationHandleService;
import com.trading.marketsignalengine.application.service.ValidatedMarketInterpretationEvaluator;
import com.trading.marketsignalengine.config.InterpretationProperties;
import com.trading.marketsignalengine.event.config.PublishTimeoutHierarchy;
import com.trading.marketsignalengine.event.publisher.MarketInterpretationSnapshotPublisher;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Boots the full Spring context with the listener stopped (no broker needed) and checks that the
 * composition root wires the single V2 runtime: exactly one {@link MarketFeaturesHandler} (the
 * interpretation handle service), exactly one V2 publisher port on the unchanged
 * {@code state.market.signals.v1} topic, a producer typed to
 * {@link MarketInterpretationSnapshotEvent}, the explicit interpretation configuration bound into
 * the assembly policy — and no V1 runtime beans at all.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:1",
        "app.kafka.schema-registry.url=mock://context-test",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.listener.concurrency=3",
        "spring.kafka.listener.ack-mode=record",
        "spring.kafka.listener.poll-timeout=1234",
        "app.kafka.publish-timeout-ms=7000",
        "app.interpretation.config-hash=cfg-context-test-1",
        "app.interpretation.version=mse-interpretation-context-v1",
        "app.interpretation.supported-feature-set-versions=mfs-features-v2,mfs-core-v2"
})
class ApplicationContextTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private InterpretationProperties interpretationProperties;
    @Autowired
    private MarketFeaturesSnapshotValidator validator;
    @Autowired
    private MarketFeaturesHandler handler;
    @Autowired
    private MarketInterpretationSnapshotPublisherPort publisher;
    @Autowired
    private KafkaListenerEndpointRegistry registry;
    @Autowired
    private PublishTimeoutHierarchy publishTimeoutHierarchy;
    @Autowired
    private ValidatedMarketInterpretationEvaluator evaluator;
    @Autowired
    private MarketInterpretationAssemblyPolicy assemblyPolicy;

    @Test
    void interpretationConfigurationComesFromExplicitProperties() {
        assertEquals("mse-interpretation-context-v1", interpretationProperties.version());
        assertEquals("cfg-context-test-1", interpretationProperties.configHash());
        assertEquals("mse-interpretation-context-v1", assemblyPolicy.interpretationVersion());
        assertEquals("cfg-context-test-1", assemblyPolicy.interpretationConfigHash());
        assertFalse(assemblyPolicy.opportunityPolicy().allowVolatileMomentumContinuation(),
                "the volatile-continuation switch is explicit configuration");
        assertEquals(Duration.ofMillis(2_000),
                assemblyPolicy.validityPolicy().momentumContinuationBaseValidityOf(
                        com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S));
    }

    @Test
    void validatorAllowlistComesFromProperties() {
        assertEquals(Set.of("mfs-features-v2", "mfs-core-v2"), validator.supportedFeatureSetVersions());
    }

    @Test
    void exactlyOneHandlerAndItIsTheInterpretationService() {
        Map<String, MarketFeaturesHandler> handlers = context.getBeansOfType(MarketFeaturesHandler.class);
        assertEquals(1, handlers.size(), "exactly one MarketFeaturesHandler");
        assertInstanceOf(MarketInterpretationHandleService.class, handler);
    }

    @Test
    void exactlyOneV2PublisherOnTheUnchangedTopic() {
        Map<String, MarketInterpretationSnapshotPublisherPort> ports =
                context.getBeansOfType(MarketInterpretationSnapshotPublisherPort.class);
        assertEquals(1, ports.size(), "exactly one V2 publisher port");
        MarketInterpretationSnapshotPublisher p =
                assertInstanceOf(MarketInterpretationSnapshotPublisher.class, publisher);
        assertEquals("state.market.signals.v1", p.topic(), "the V1 topic name stays");
        assertEquals(Duration.ofMillis(7000), p.publishTimeout());
        // request (3000) < delivery (5000) from application.yml < publish (7000) from this test
        assertEquals(new PublishTimeoutHierarchy(3000L, 5000L, 7000L), publishTimeoutHierarchy);
    }

    @Test
    void producerIsTypedToTheV2InterpretationEvent() {
        String[] names = context.getBeanNamesForType(ResolvableType.forClassWithGenerics(
                ProducerFactory.class, String.class, MarketInterpretationSnapshotEvent.class));
        assertTrue(names.length >= 1, "a ProducerFactory<String, MarketInterpretationSnapshotEvent> must exist");
        assertFalse(context.containsBean("marketSignalsProducerFactory"), "no V1 producer factory bean");
        assertFalse(context.containsBean("marketSignalSnapshotPublisher"), "no V1 publisher bean");
    }

    @Test
    void liveHandlerUsesTheSharedValidatedEvaluator() {
        assertNotNull(evaluator);
        assertEquals(Set.of("mfs-features-v2", "mfs-core-v2"), evaluator.validator().supportedFeatureSetVersions());
        assertEquals(assemblyPolicy, evaluator.assemblyPolicy(),
                "live and replay share the one policy-bound evaluator");
    }

    @Test
    void listenerPropertiesReachTheContainer() {
        // spring.kafka.listener.* must be honoured by the factory (Boot configurer), not ignored.
        assertNotNull(context.getBean("marketFeaturesKafkaListenerContainerFactory"));
        assertFalse(registry.getListenerContainers().isEmpty(), "listener must be registered");
        MessageListenerContainer container = registry.getListenerContainers().iterator().next();
        assertFalse(container.isRunning(), "auto-startup=false must keep the listener stopped");
        assertFalse(container.isAutoStartup());
        assertEquals(3, ((org.springframework.kafka.listener.ConcurrentMessageListenerContainer<?, ?>) container)
                .getConcurrency());
        ContainerProperties props = container.getContainerProperties();
        assertEquals(ContainerProperties.AckMode.RECORD, props.getAckMode());
        assertEquals(1234L, props.getPollTimeout());
        assertTrue(props.getTopics() != null && props.getTopics().length == 1);
        assertEquals("market.feature.snapshot.v1", props.getTopics()[0]);
        // the input side still consumes MarketFeaturesSnapshotEvent
        assertNotNull(context.getBeanNamesForType(ResolvableType.forClassWithGenerics(
                org.springframework.kafka.core.ConsumerFactory.class, String.class, MarketFeaturesSnapshotEvent.class)));
    }
}
