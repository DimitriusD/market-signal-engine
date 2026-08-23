package com.trading.marketsignalengine.application.domain.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.StandardSignalEngine;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.application.service.MarketSignalHandleService;
import com.trading.marketsignalengine.application.service.ValidatedMarketSignalEvaluator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * MFS v2 structural / compatibility validation. The dividing line under test: a contract
 * contradiction throws {@link InvalidMarketFeaturesSnapshotException} (→ DLT), whereas a valid but
 * bad market state (DEGRADED / UNSAFE / NO_DATA / warm-up / stale / future event / failed calculator)
 * passes and is left to the quality gate.
 */
class MarketFeaturesSnapshotValidatorTest {

    private static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    private static final Instant COMPUTED_AT = SignalRuleTestSupport.COMPUTED_AT;

    private final MarketFeaturesSnapshotValidator validator =
            new MarketFeaturesSnapshotValidator(Set.of("mfs-features-v2"));

    // ------------------------------------------------------------------ valid inputs

    @Test
    void validTradeSnapshotPasses() {
        assertDoesNotThrow(() -> validator.validate(valid().build()));
    }

    @Test
    void validOrderBookSnapshotPasses() {
        assertDoesNotThrow(() -> validator.validate(valid()
                .triggerSource("ORDER_BOOK_L2_SNAPSHOT")
                .build()));
    }

    @Test
    void validTimerSnapshotWithZeroSourceEventTimePasses() {
        // A clock tick has no market event: exchangeTs/receivedTs are epoch zero upstream and the
        // as-of instant is the processing instant.
        assertDoesNotThrow(() -> validator.validate(valid()
                .triggerSource("TIMER")
                .eventTime(Instant.EPOCH)
                .receivedAt(Instant.EPOCH)
                .evaluationTs(COMPUTED_AT)
                .build()));
    }

    @Test
    void timerEvaluationAfterComputedAtIsAContradiction() {
        assertRejected(valid()
                .triggerSource("TIMER")
                .eventTime(Instant.EPOCH)
                .evaluationTs(COMPUTED_AT.plusMillis(1))
                .build(), "TIMER evaluationTs");
    }

    // ------------------------------------------------------------------ identity / lineage

    @Test
    void nullSnapshotFails() {
        assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> validator.validate(null));
    }

    @Test
    void blankIdentityFields() {
        assertRejected(valid().snapshotId(" ").build(), "snapshotId");
        assertRejected(valid().instrumentId("").build(), "instrumentId");
        assertRejected(valid().featureSetVersion(null).build(), "featureSetVersion");
    }

    @Test
    void blankConfigHashFails() {
        assertRejected(valid().configHash("").build(), "configHash");
        assertRejected(valid().configHash(null).build(), "configHash");
    }

    // ------------------------------------------------------------------ compatibility

    @Test
    void unsupportedFeatureSetVersionFailsClosed() {
        InvalidMarketFeaturesSnapshotException ex = assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> validator.validate(valid().featureSetVersion("mfs-features-v3").build()));

        assertTrue(ex.getMessage().contains("mfs-features-v3"));
        assertTrue(ex.getMessage().contains("mfs-features-v2"));
    }

    @Test
    void versionMatchIsExactNotPrefix() {
        assertRejected(valid().featureSetVersion("mfs-features-v2-rc1").build(), "unsupported featureSetVersion");
        assertRejected(valid().featureSetVersion("MFS-FEATURES-V2").build(), "unsupported featureSetVersion");
    }

    @Test
    void unsupportedSchemaVersionFails() {
        assertRejected(valid().schemaVersion(2).build(), "schemaVersion");
        assertRejected(valid().schemaVersion(0).build(), "schemaVersion");
        assertRejected(valid().schemaVersion(null).build(), "schemaVersion");
    }

    @Test
    void allowlistCanHoldSeveralVersions() {
        MarketFeaturesSnapshotValidator wide =
                new MarketFeaturesSnapshotValidator(Set.of("mfs-features-v2", " mfs-core-v2 "));

        assertDoesNotThrow(() -> wide.validate(valid().featureSetVersion("mfs-core-v2").build()));
        assertEquals(Set.of("mfs-features-v2", "mfs-core-v2"), wide.supportedFeatureSetVersions());
    }

    @Test
    void emptyOrBlankAllowlistIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new MarketFeaturesSnapshotValidator(Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new MarketFeaturesSnapshotValidator(null));
        assertThrows(IllegalArgumentException.class, () -> new MarketFeaturesSnapshotValidator(Set.of(" ")));
    }

    // ------------------------------------------------------------------ timestamps / trigger

    @Test
    void nullOrZeroEvaluationTsFails() {
        assertRejected(valid().evaluationTs(null).build(), "evaluationTs");
        assertRejected(valid().evaluationTs(Instant.EPOCH).build(), "evaluationTs");
    }

    @Test
    void missingOrZeroComputedAtFails() {
        assertRejected(valid().computedAt(null).build(), "computedAt");
        assertRejected(valid().computedAt(Instant.EPOCH).build(), "computedAt");
    }

    @Test
    void nullEventTimeFails() {
        assertRejected(valid().eventTime(null).build(), "eventTime");
    }

    @Test
    void zeroEventTimeFailsForMarketEventTriggers() {
        assertRejected(valid().eventTime(Instant.EPOCH).evaluationTs(Instant.EPOCH).build(), "evaluationTs");
        // evaluationTs positive but the source event time is zero: contradiction for TRADE
        assertRejected(valid().eventTime(Instant.EPOCH).build(), "eventTime must be positive for TRADE");
    }

    @Test
    void unknownTriggerSourceIsRejected() {
        assertRejected(valid().triggerSource("UNKNOWN").build(), "unsupported triggerSource 'UNKNOWN'");
    }

    @Test
    void arbitraryTriggerSourceIsRejected() {
        assertRejected(valid().triggerSource("MARKET_EVENT").build(), "unsupported triggerSource");
        assertRejected(valid().triggerSource(" ").build(), "triggerSource must not be blank");
        assertRejected(valid().triggerSource(null).build(), "triggerSource must not be blank");
    }

    @Test
    void marketEventEvaluationTsMustEqualSourceEventTime() {
        assertRejected(valid().evaluationTs(EVENT_TIME.plusMillis(35)).build(), "must equal the source event time");
        assertRejected(valid().triggerSource("ORDER_BOOK_L2_SNAPSHOT").evaluationTs(EVENT_TIME.minusMillis(1)).build(),
                "must equal the source event time");
    }

    @Test
    void futureSourceTimestampIsValidWhenReportedHonestly() {
        // trigger exchangeTs ahead of the producer clock: evaluationTs > computedAt, flagged upstream
        // as futureEventDetected (DEGRADED / FUTURE_EVENT) — a valid event, not a contract error.
        Instant future = COMPUTED_AT.plusMillis(500);
        assertDoesNotThrow(() -> validator.validate(valid()
                .eventTime(future)
                .evaluationTs(future)
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .futureEventDetected(true)
                        .qualityReasons(List.of("FUTURE_EVENT"))
                        .build())
                .build()));
    }

    @Test
    void futureSourceTimestampWithoutFutureEventFlagIsAContradiction() {
        Instant future = COMPUTED_AT.plusMillis(500);
        assertRejected(valid()
                .eventTime(future)
                .evaluationTs(future)
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .futureEventDetected(false)
                        .staleTrades(true)
                        .qualityReasons(List.of("STALE_TRADES"))
                        .build())
                .build(), "futureEventDetected=false");
    }

    // ------------------------------------------------------------------ quality presence

    @Test
    void nullQualityFails() {
        assertRejected(valid().quality(null).build(), "quality must not be null");
    }

    @Test
    void nullQualityStatusFails() {
        assertRejected(valid().quality(tradable().toBuilder().status(null).build()).build(),
                "quality.status must not be null");
    }

    // ------------------------------------------------------------------ OK contradictions

    @Test
    void okWithUntrustedBookIsAContradiction() {
        assertRejected(valid().quality(tradable().toBuilder().sourceOrderBookTrusted(false).build()).build(),
                "quality.status=OK contradicts sourceOrderBookTrusted=false");
    }

    @Test
    void okWithWarmingUpIsAContradiction() {
        assertRejected(valid().quality(tradable().toBuilder().warmingUp(true).build()).build(),
                "quality.status=OK contradicts warmingUp=true");
    }

    @Test
    void okWithCalculatorFailureIsAContradiction() {
        assertRejected(valid()
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("trade-flow")).totalFeatureGroups(4).build())
                .build(), "quality.status=OK contradicts diagnostics.failedFeatureGroups");
    }

    @Test
    void okWithOtherImpairmentsIsAContradiction() {
        assertRejected(valid().quality(tradable().toBuilder().syncStatus(SyncStatus.RECOVERING).build()).build(),
                "syncStatus=RECOVERING");
        assertRejected(valid().quality(tradable().toBuilder().staleOrderBookState(true).build()).build(),
                "staleOrderBookState=true");
        assertRejected(valid().quality(tradable().toBuilder().staleTrades(true).build()).build(),
                "staleTrades=true");
        assertRejected(valid().quality(tradable().toBuilder().incompleteBook(true).build()).build(),
                "incompleteBook=true");
        assertRejected(valid().quality(tradable().toBuilder().futureEventDetected(true).build()).build(),
                "futureEventDetected=true");
        assertRejected(valid().quality(tradable().toBuilder().qualityReasons(List.of("STALE_TRADES")).build()).build(),
                "qualityReasons=[STALE_TRADES]");
    }

    // ------------------------------------------------------------------ valid non-OK states

    @Test
    void degradedWarmingUpIsValidInput() {
        assertDoesNotThrow(() -> validator.validate(valid()
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .warmingUp(true)
                        .qualityReasons(List.of("WARMING_UP"))
                        .build())
                .build()));
    }

    @Test
    void degradedCalculatorFailureIsValidInput() {
        assertDoesNotThrow(() -> validator.validate(valid()
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("CALCULATOR_FAILURE"))
                        .build())
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("short-term-regime")).totalFeatureGroups(4).build())
                .build()));
    }

    @Test
    void degradedWithTradeHistoryGapOrStaleTradesIsValidInput() {
        assertDoesNotThrow(() -> validator.validate(valid()
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("TRADE_HISTORY_GAP"))
                        .build())
                .build()));
        assertDoesNotThrow(() -> validator.validate(valid()
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .staleTrades(true)
                        .qualityReasons(List.of("STALE_TRADES"))
                        .build())
                .build()));
    }

    @Test
    void degradedWithoutAnyCauseIsAContradiction() {
        assertRejected(valid().quality(tradable().toBuilder().status(FeatureQualityStatus.DEGRADED).build()).build(),
                "quality.status=DEGRADED without any degraded cause");
    }

    @Test
    void unsafeUntrustedBookIsValidInput() {
        assertDoesNotThrow(() -> validator.validate(valid()
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.UNSAFE)
                        .sourceOrderBookTrusted(false)
                        .sourceOrderBookReason("CROSSED_BOOK")
                        .qualityReasons(List.of("BOOK_UNTRUSTED"))
                        .build())
                .build()));
    }

    @Test
    void unsafeOutOfSyncBookIsValidInput() {
        assertDoesNotThrow(() -> validator.validate(valid()
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.UNSAFE)
                        .syncStatus(SyncStatus.OUT_OF_SYNC)
                        .qualityReasons(List.of("BOOK_OUT_OF_SYNC"))
                        .build())
                .build()));
    }

    @Test
    void unsafeWithoutAnUnsafeCauseIsAContradiction() {
        assertRejected(valid().quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.UNSAFE)
                        .staleTrades(true)
                        .qualityReasons(List.of("STALE_TRADES"))
                        .build()).build(),
                "quality.status=UNSAFE without an unsafe cause");
    }

    @Test
    void noDataIsValidInput() {
        assertDoesNotThrow(() -> validator.validate(valid()
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.NO_DATA)
                        .syncStatus(SyncStatus.OUT_OF_SYNC)
                        .sourceOrderBookTrusted(false)
                        .staleOrderBookState(true)
                        .staleTrades(true)
                        .incompleteBook(true)
                        .qualityReasons(List.of("NO_MARKET_DATA"))
                        .build())
                .build()));
    }

    @Test
    void noDataWithoutNoMarketDataReasonIsAContradiction() {
        assertRejected(valid().quality(tradable().toBuilder().status(FeatureQualityStatus.NO_DATA).build()).build(),
                "NO_MARKET_DATA");
    }

    @Test
    void untrustedBookAloneIsNotAContractError() {
        // sourceOrderBookTrusted=false is legitimate for UNSAFE / NO_DATA; it only contradicts OK.
        assertDoesNotThrow(() -> validator.validate(valid()
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.UNSAFE)
                        .sourceOrderBookTrusted(false)
                        .build())
                .build()));
    }

    // ------------------------------------------------------------------ diagnostics

    @Test
    void diagnosticsAcceptTheRealFeatureGroupIdsAndRejectBlankOnes() {
        FeatureQuality degraded = tradable().toBuilder()
                .status(FeatureQualityStatus.DEGRADED)
                .qualityReasons(List.of("CALCULATOR_FAILURE"))
                .build();
        for (String group : MarketFeaturesSnapshotValidator.KNOWN_FEATURE_GROUPS) {
            assertDoesNotThrow(() -> validator.validate(valid()
                    .quality(degraded)
                    .diagnostics(FeatureDiagnostics.builder()
                            .failedFeatureGroups(List.of(group)).totalFeatureGroups(4).build())
                    .build()), group);
        }
        assertRejected(valid()
                .quality(degraded)
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of(" ")).totalFeatureGroups(4).build())
                .build(), "failedFeatureGroups must not contain blank ids");
        assertRejected(valid()
                .quality(degraded)
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("bbo", "order-book")).totalFeatureGroups(1).build())
                .build(), "failed groups out of");
    }

    @Test
    void missingDiagnosticsIsTolerated() {
        assertDoesNotThrow(() -> validator.validate(valid().diagnostics(null).build()));
    }

    // ------------------------------------------------------------------ end to end: not reaching engine/publisher

    @Test
    void invalidInputNeverReachesEngineOrPublisher() {
        List<Instant> engineCalls = new ArrayList<>();
        List<Object> published = new ArrayList<>();
        MarketSignalEngine production = StandardSignalEngine.create(
                SignalConfiguration.defaults(), Clock.fixed(EVENT_TIME, ZoneOffset.UTC));
        MarketSignalEngine recording = new MarketSignalEngine() {
            @Override
            public com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot evaluate(
                    MarketFeaturesSnapshot features) {
                throw new AssertionError("live path must use the explicit-instant overload");
            }

            @Override
            public com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot evaluate(
                    MarketFeaturesSnapshot features, Instant evaluatedAt) {
                engineCalls.add(evaluatedAt);
                return production.evaluate(features, evaluatedAt);
            }
        };
        MarketSignalSnapshotPublisherPort publisher = published::add;
        MarketSignalHandleService service = new MarketSignalHandleService(
                new ValidatedMarketSignalEvaluator(validator, recording), publisher,
                Clock.fixed(EVENT_TIME, ZoneOffset.UTC));

        List<MarketFeaturesSnapshot> invalid = List.of(
                valid().configHash("").build(),
                valid().schemaVersion(7).build(),
                valid().triggerSource("UNKNOWN").build(),
                valid().quality(null).build(),
                valid().quality(tradable().toBuilder().warmingUp(true).build()).build());
        for (MarketFeaturesSnapshot snapshot : invalid) {
            assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> service.handle(snapshot));
        }
        assertTrue(engineCalls.isEmpty(), "engine must not see invalid input");
        assertTrue(published.isEmpty(), "publisher must not see invalid input");

        // ...while a valid but DEGRADED snapshot does reach the engine (and yields a no-trade output).
        service.handle(valid()
                .quality(tradable().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .warmingUp(true)
                        .qualityReasons(List.of("WARMING_UP"))
                        .build())
                .build());
        assertEquals(1, engineCalls.size());
        assertEquals(1, published.size());
    }

    // ------------------------------------------------------------------ fixtures

    private static void assertRejected(MarketFeaturesSnapshot snapshot, String expectedMessagePart) {
        MarketFeaturesSnapshotValidator v = new MarketFeaturesSnapshotValidator(Set.of("mfs-features-v2"));
        InvalidMarketFeaturesSnapshotException ex =
                assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> v.validate(snapshot));
        assertTrue(ex.getMessage().contains(expectedMessagePart),
                "expected message to contain '" + expectedMessagePart + "' but was: " + ex.getMessage());
    }

    /** A complete, contract-valid MFS v2 TRADE-triggered snapshot with OK quality. */
    private static MarketFeaturesSnapshot.MarketFeaturesSnapshotBuilder valid() {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(TradeFlowFeature.builder()
                        .window5s(TradeFlowWindow.builder()
                                .signedFlowImbalance(new java.math.BigDecimal("0.70"))
                                .tradeCount(50)
                                .build())
                        .build());
    }

    private static FeatureQuality tradable() {
        return SignalRuleTestSupport.tradableQuality();
    }
}
