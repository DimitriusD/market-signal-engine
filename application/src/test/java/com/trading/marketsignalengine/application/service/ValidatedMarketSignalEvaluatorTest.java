package com.trading.marketsignalengine.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.trading.marketsignalengine.application.replay.ReplayHarness;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Live/replay parity: {@link MarketSignalHandleService} (live) and {@link ReplayHarness} (replay)
 * share one {@link ValidatedMarketSignalEvaluator}. Same input + same evaluatedAt + same
 * configuration ⇒ same snapshot, or the same validation exception; invalid input never reaches the
 * engine or the publisher on either path.
 */
class ValidatedMarketSignalEvaluatorTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-01-01T00:00:00.100Z");
    private static final Clock LIVE_CLOCK = Clock.fixed(EVALUATED_AT, ZoneOffset.UTC);

    private final MarketFeaturesSnapshotValidator validator =
            new MarketFeaturesSnapshotValidator(Set.of(SignalRuleTestSupport.FEATURE_SET_VERSION));

    // ------------------------------------------------------------------ evaluator contract

    @Test
    void validInputIsValidatedThenEvaluated() {
        RecordingEngine engine = new RecordingEngine();
        ValidatedMarketSignalEvaluator evaluator = new ValidatedMarketSignalEvaluator(validator, engine);

        MarketSignalSnapshot snapshot = evaluator.evaluate(SignalRuleTestSupport.defaultFeatures(), EVALUATED_AT);

        assertNotNull(snapshot);
        assertEquals(List.of(EVALUATED_AT), engine.evaluatedAt);
        assertEquals(EVALUATED_AT, snapshot.createdAt());
    }

    @Test
    void nullInputIsAValidationExceptionAndTheEngineIsNotCalled() {
        RecordingEngine engine = new RecordingEngine();
        ValidatedMarketSignalEvaluator evaluator = new ValidatedMarketSignalEvaluator(validator, engine);

        assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> evaluator.evaluate(null, EVALUATED_AT));

        assertTrue(engine.evaluatedAt.isEmpty());
    }

    @Test
    void invalidInputNeverReachesTheEngine() {
        RecordingEngine engine = new RecordingEngine();
        ValidatedMarketSignalEvaluator evaluator = new ValidatedMarketSignalEvaluator(validator, engine);

        assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> evaluator.evaluate(
                SignalRuleTestSupport.tradableFeaturesBuilder().featureSetVersion("mfs-features-v99").build(),
                EVALUATED_AT));
        assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> evaluator.evaluate(
                SignalRuleTestSupport.tradableFeaturesBuilder().configHash(" ").build(), EVALUATED_AT));

        assertTrue(engine.evaluatedAt.isEmpty(), "validator must run before the engine");
    }

    @Test
    void nullEvaluatedAtIsRejectedBeforeTheEngine() {
        RecordingEngine engine = new RecordingEngine();
        ValidatedMarketSignalEvaluator evaluator = new ValidatedMarketSignalEvaluator(validator, engine);

        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(SignalRuleTestSupport.defaultFeatures(), null));

        assertTrue(engine.evaluatedAt.isEmpty());
    }

    @Test
    void nullEngineResultFailsFast() {
        ValidatedMarketSignalEvaluator evaluator = new ValidatedMarketSignalEvaluator(validator, new MarketSignalEngine() {
            @Override
            public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features) {
                return null;
            }

            @Override
            public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features, Instant evaluatedAt) {
                return null;
            }
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> evaluator.evaluate(SignalRuleTestSupport.defaultFeatures(), EVALUATED_AT));
        assertTrue(ex.getMessage().contains("snap-1"));
    }

    @Test
    void sameInputAndEvaluatedAtYieldTheSameSnapshot() {
        ValidatedMarketSignalEvaluator evaluator = new ValidatedMarketSignalEvaluator(validator, productionEngine());
        MarketFeaturesSnapshot features = SignalRuleTestSupport.defaultFeatures();

        MarketSignalSnapshot first = evaluator.evaluate(features, EVALUATED_AT);
        MarketSignalSnapshot second = evaluator.evaluate(features, EVALUATED_AT);
        MarketSignalSnapshot third = new ValidatedMarketSignalEvaluator(validator, productionEngine())
                .evaluate(features, EVALUATED_AT);

        assertEquals(first, second);
        assertEquals(first, third);
        assertEquals(first.signalSnapshotId(), third.signalSnapshotId());
    }

    // ------------------------------------------------------------------ live == replay

    @Test
    void validInputPassesLiveAndReplayWithIdenticalOutput() {
        ValidatedMarketSignalEvaluator evaluator = new ValidatedMarketSignalEvaluator(validator, productionEngine());
        RecordingPublisher publisher = new RecordingPublisher();
        MarketSignalHandleService live = new MarketSignalHandleService(evaluator, publisher, LIVE_CLOCK);
        ReplayHarness replay = new ReplayHarness(evaluator);
        MarketFeaturesSnapshot features = SignalRuleTestSupport.defaultFeatures();

        live.handle(features);
        List<MarketSignalSnapshot> replayed = replay.replay(List.of(features), ReplayHarness.fixed(EVALUATED_AT));

        assertEquals(1, publisher.published.size());
        assertEquals(publisher.published.getFirst(), replayed.getFirst());
    }

    @Test
    void unsupportedFeatureSetVersionIsRejectedIdenticallyLiveAndReplay() {
        assertRejectedOnBothPaths(
                SignalRuleTestSupport.tradableFeaturesBuilder().featureSetVersion("mfs-features-v99").build());
    }

    @Test
    void blankConfigHashIsRejectedIdenticallyLiveAndReplay() {
        assertRejectedOnBothPaths(SignalRuleTestSupport.tradableFeaturesBuilder().configHash("").build());
    }

    @Test
    void replayDoesNotPublishAndDoesNotReachTheEngineForInvalidInput() {
        RecordingEngine engine = new RecordingEngine();
        ReplayHarness replay = new ReplayHarness(new ValidatedMarketSignalEvaluator(validator, engine));
        MarketFeaturesSnapshot invalid = SignalRuleTestSupport.tradableFeaturesBuilder().triggerSource("UNKNOWN").build();

        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> replay.replay(List.of(invalid), ReplayHarness.fixed(EVALUATED_AT)));

        assertTrue(engine.evaluatedAt.isEmpty());
    }

    @Test
    void replayRejectsNullInputLikeLive() {
        List<MarketFeaturesSnapshot> inputs = new ArrayList<>();
        inputs.add(null);
        ReplayHarness replay = ReplayHarness.standard(SignalConfiguration.defaults());

        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> replay.replay(inputs, ReplayHarness.fixed(EVALUATED_AT)));
    }

    @Test
    void standardReplayHarnessUsesTheDefaultAllowlist() {
        ReplayHarness replay = ReplayHarness.standard(SignalConfiguration.defaults());

        assertEquals(ReplayHarness.DEFAULT_SUPPORTED_FEATURE_SET_VERSIONS,
                replay.evaluator().validator().supportedFeatureSetVersions());
        assertFalse(replay.replay(List.of(SignalRuleTestSupport.defaultFeatures()),
                ReplayHarness.fixed(EVALUATED_AT)).isEmpty());
    }

    private void assertRejectedOnBothPaths(MarketFeaturesSnapshot invalid) {
        RecordingEngine engine = new RecordingEngine();
        ValidatedMarketSignalEvaluator evaluator = new ValidatedMarketSignalEvaluator(validator, engine);
        RecordingPublisher publisher = new RecordingPublisher();
        MarketSignalHandleService live = new MarketSignalHandleService(evaluator, publisher, LIVE_CLOCK);
        ReplayHarness replay = new ReplayHarness(evaluator);

        InvalidMarketFeaturesSnapshotException liveEx =
                assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> live.handle(invalid));
        InvalidMarketFeaturesSnapshotException replayEx = assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> replay.replay(List.of(invalid), ReplayHarness.fixed(EVALUATED_AT)));

        assertEquals(liveEx.getMessage(), replayEx.getMessage(), "live and replay must fail for the same reason");
        assertTrue(publisher.published.isEmpty(), "invalid input must never be published");
        assertTrue(engine.evaluatedAt.isEmpty(), "invalid input must never reach the engine");
    }

    private static MarketSignalEngine productionEngine() {
        return StandardSignalEngine.create(SignalConfiguration.defaults(), LIVE_CLOCK);
    }

    /** Production rules, but records every explicit evaluation instant it is asked for. */
    private static final class RecordingEngine implements MarketSignalEngine {
        private final MarketSignalEngine delegate = productionEngine();
        final List<Instant> evaluatedAt = new ArrayList<>();

        @Override
        public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features) {
            throw new AssertionError("the validated evaluator must always pass an explicit evaluatedAt");
        }

        @Override
        public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features, Instant at) {
            evaluatedAt.add(at);
            return delegate.evaluate(features, at);
        }
    }

    private static final class RecordingPublisher implements MarketSignalSnapshotPublisherPort {
        final List<MarketSignalSnapshot> published = new ArrayList<>();

        @Override
        public void publish(MarketSignalSnapshot snapshot) {
            published.add(snapshot);
        }
    }
}
