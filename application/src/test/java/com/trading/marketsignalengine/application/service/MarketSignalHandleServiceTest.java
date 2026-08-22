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

    private final RecordingMetrics metrics = new RecordingMetrics();
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final MarketSignalHandleService service = new MarketSignalHandleService(
            StandardSignalEngine.create(SignalConfiguration.defaults(),
                    Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC)),
            publisher,
            new MarketFeaturesSnapshotValidator(Set.of("mfs-core-v1")),
            metrics);

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
    void validationFailureShortCircuitsBeforeEvaluationAndPublish() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .featureSetVersion("mfs-unknown-v9")
                .build();

        assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> service.handle(features));

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
