package com.trading.marketsignalengine.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.StandardSignalEngine;
import com.trading.marketsignalengine.application.domain.validation.InvalidMarketFeaturesSnapshotException;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.application.port.output.SignalMetricsPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarketSignalHandleServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final RecordingMetrics metrics = new RecordingMetrics();
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final MarketFeaturesSnapshotValidator validator =
            new MarketFeaturesSnapshotValidator(Set.of(SignalRuleTestSupport.FEATURE_SET_VERSION));
    private final MarketSignalEngine engine = StandardSignalEngine.create(SignalConfiguration.defaults(), CLOCK);
    private final MarketSignalHandleService service = new MarketSignalHandleService(
            new ValidatedMarketSignalEvaluator(validator, engine), publisher, CLOCK, metrics);

    @Test
    void validInputIsEvaluatedPublishedAndReportedInOrder() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.defaultFeatures();

        service.handle(features);

        assertEquals(1, publisher.published.size());
        assertEquals(List.of("evaluated", "published"), metrics.events);
        assertSame(publisher.published.getFirst(), metrics.lastSnapshot);
        assertNotNull(metrics.lastEvaluation);
        assertNotNull(metrics.lastPublish);
    }

    @Test
    void liveEvaluationInstantComesFromTheInjectedClock() {
        service.handle(SignalRuleTestSupport.defaultFeatures());

        MarketSignalSnapshot snapshot = publisher.published.getFirst();
        assertEquals(NOW, snapshot.createdAt());
        assertEquals(NOW.plusMillis(snapshot.ttlMs()), snapshot.validUntil());
    }

    @Test
    void validationFailureShortCircuitsBeforeEvaluationAndPublish() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .featureSetVersion("mfs-unknown-v9")
                .build();

        assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> service.handle(features));

        assertTrue(publisher.published.isEmpty());
        assertTrue(metrics.events.isEmpty());
    }

    @Test
    void nullInputIsAValidationFailureAndNeverReachesThePublisher() {
        assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> service.handle(null));

        assertTrue(publisher.published.isEmpty());
        assertTrue(metrics.events.isEmpty());
    }

    @Test
    void nullEngineOutputFailsFastAndIsNotPublished() {
        MarketSignalHandleService broken = new MarketSignalHandleService(
                new ValidatedMarketSignalEvaluator(validator, new MarketSignalEngine() {
                    @Override
                    public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features) {
                        return null;
                    }

                    @Override
                    public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features, Instant evaluatedAt) {
                        return null;
                    }
                }), publisher, CLOCK, metrics);

        assertThrows(IllegalStateException.class, () -> broken.handle(SignalRuleTestSupport.defaultFeatures()));

        assertTrue(publisher.published.isEmpty());
        assertTrue(metrics.events.isEmpty());
    }

    @Test
    void engineFailureIsNotSwallowed() {
        MarketSignalHandleService broken = new MarketSignalHandleService(
                new ValidatedMarketSignalEvaluator(validator, new MarketSignalEngine() {
                    @Override
                    public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features) {
                        throw new ArithmeticException("boom");
                    }

                    @Override
                    public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features, Instant evaluatedAt) {
                        throw new ArithmeticException("boom");
                    }
                }), publisher, CLOCK, metrics);

        ArithmeticException ex = assertThrows(ArithmeticException.class,
                () -> broken.handle(SignalRuleTestSupport.defaultFeatures()));

        assertEquals("boom", ex.getMessage());
        assertTrue(publisher.published.isEmpty());
        assertTrue(metrics.events.isEmpty());
    }

    @Test
    void publishFailureIsReportedAndRethrown() {
        publisher.failWith = new IllegalStateException("broker down");
        MarketFeaturesSnapshot features = SignalRuleTestSupport.defaultFeatures();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.handle(features));

        assertEquals("broker down", ex.getMessage());
        assertEquals(List.of("evaluated", "publishFailed"), metrics.events);
        assertSame(ex, metrics.lastError);
    }

    private static final class RecordingPublisher implements MarketSignalSnapshotPublisherPort {
        final List<MarketSignalSnapshot> published = new ArrayList<>();
        RuntimeException failWith;

        @Override
        public void publish(MarketSignalSnapshot snapshot) {
            if (failWith != null) {
                throw failWith;
            }
            published.add(snapshot);
        }
    }

    private static final class RecordingMetrics implements SignalMetricsPort {
        final List<String> events = new ArrayList<>();
        MarketSignalSnapshot lastSnapshot;
        Duration lastEvaluation;
        Duration lastPublish;
        Throwable lastError;

        @Override
        public void evaluated(MarketFeaturesSnapshot features, MarketSignalSnapshot snapshot, Duration evaluation) {
            events.add("evaluated");
            lastSnapshot = snapshot;
            lastEvaluation = evaluation;
        }

        @Override
        public void published(MarketSignalSnapshot snapshot, Duration publish) {
            events.add("published");
            lastPublish = publish;
        }

        @Override
        public void publishFailed(MarketSignalSnapshot snapshot, Duration publish, Throwable error) {
            events.add("publishFailed");
            lastError = error;
        }
    }
}
