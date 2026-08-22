package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualitySignalRuleTest {

    private final QualitySignalRule rule = new QualitySignalRule();

    @Test
    void tradableQualityEmitsDataTradable() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.defaultFeatures();
        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(1, signals.size());
        assertEquals(SignalType.DATA_TRADABLE, signals.getFirst().type());
    }

    @Test
    void missingQualityEmitsNoTradeQualityMissingNotGenericNoTradeCondition() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(null);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(1, signals.size());
        assertEquals(SignalType.NO_TRADE_QUALITY_MISSING, signals.getFirst().type());
        // The engine is the single emitter of NO_TRADE_CONDITION; the rule must not co-own it.
        assertFalse(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_CONDITION));
    }

    @Test
    void outOfSyncQualityEmitsNoTradeOutOfSync() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.OUT_OF_SYNC)
                .staleOrderBookState(false)
                .staleTrades(false)
                .incompleteBook(false)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_OUT_OF_SYNC));
    }

    @Test
    void staleSyncStatusEmitsNoTradeStaleBook() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.STALE)
                .staleOrderBookState(false)
                .staleTrades(false)
                .incompleteBook(false)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_STALE_BOOK));
    }

    @Test
    void staleOrderBookStateEmitsNoTradeStaleBook() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.IN_SYNC)
                .staleOrderBookState(true)
                .staleTrades(false)
                .incompleteBook(false)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_STALE_BOOK));
    }

    @Test
    void staleSyncStatusAndStateEmitSingleNoTradeStaleBook() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.STALE)
                .staleOrderBookState(true)
                .staleTrades(false)
                .incompleteBook(false)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(1, signals.stream().filter(s -> s.type() == SignalType.NO_TRADE_STALE_BOOK).count());
    }

    @Test
    void recoveringBookEmitsNoTradeRecoveringBook() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.RECOVERING)
                .staleOrderBookState(false)
                .staleTrades(false)
                .incompleteBook(false)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_RECOVERING_BOOK));
    }

    @Test
    void staleTradesEmitsNoTradeStaleTrades() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.IN_SYNC)
                .staleOrderBookState(false)
                .staleTrades(true)
                .incompleteBook(false)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_STALE_TRADES));
    }

    @Test
    void incompleteBookEmitsNoTradeIncompleteBook() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.IN_SYNC)
                .staleOrderBookState(false)
                .staleTrades(false)
                .incompleteBook(true)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_INCOMPLETE_BOOK));
    }

    @Test
    void outOfSyncSignalContainsSyncStatus() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.OUT_OF_SYNC)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        MarketSignal signal = first(rule.evaluate(SignalRuleTestSupport.context(features)),
                SignalType.NO_TRADE_OUT_OF_SYNC);

        assertEquals("OUT_OF_SYNC", signal.attributes().get("syncStatus"));
        assertEquals("OUT_OF_SYNC_OR_UNKNOWN", signal.attributes().get("qualityReason"));
        assertEquals("false", signal.attributes().get("staleOrderBookState"));
    }

    @Test
    void staleBookSignalContainsAgeAttributes() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.STALE)
                .staleOrderBookState(true)
                .orderBookStateAgeMs(1234L)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        MarketSignal signal = first(rule.evaluate(SignalRuleTestSupport.context(features)),
                SignalType.NO_TRADE_STALE_BOOK);

        assertEquals("STALE", signal.attributes().get("syncStatus"));
        assertEquals("STALE_BOOK", signal.attributes().get("qualityReason"));
        assertEquals("true", signal.attributes().get("staleOrderBookState"));
        assertEquals("1234", signal.attributes().get("orderBookStateAgeMs"));
    }

    @Test
    void staleTradesSignalContainsTradeAge() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.IN_SYNC)
                .staleTrades(true)
                .tradeAgeMs(5678L)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        MarketSignal signal = first(rule.evaluate(SignalRuleTestSupport.context(features)),
                SignalType.NO_TRADE_STALE_TRADES);

        assertEquals("STALE_TRADES", signal.attributes().get("qualityReason"));
        assertEquals("true", signal.attributes().get("staleTrades"));
        assertEquals("5678", signal.attributes().get("tradeAgeMs"));
    }

    @Test
    void tradableSignalContainsQualityAttributes() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.defaultFeatures();

        MarketSignal signal = first(rule.evaluate(SignalRuleTestSupport.context(features)),
                SignalType.DATA_TRADABLE);

        assertEquals("DATA_TRADABLE", signal.attributes().get("qualityReason"));
        assertEquals("IN_SYNC", signal.attributes().get("syncStatus"));
        assertEquals("false", signal.attributes().get("staleOrderBookState"));
        assertEquals("false", signal.attributes().get("staleTrades"));
        assertEquals("false", signal.attributes().get("incompleteBook"));
    }

    // ------------------------------------------------------------ aggregate status gate (v8)

    @Test
    void degradedStatusWithCleanPerSourceFlagsIsNoTradeDegraded() {
        // Decision 8.4: DEGRADED is a hard block for paper even when every per-source flag is clean
        // (e.g. warm-up of the 60s window, failed calculator, trade-history gap).
        FeatureQuality quality = cleanFlags()
                .status(FeatureQualityStatus.DEGRADED)
                .qualityReasons(List.of("WARMING_UP"))
                .warmingUp(true)
                .build();

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(SignalRuleTestSupport.withQuality(quality)));

        assertEquals(1, signals.size());
        MarketSignal signal = signals.getFirst();
        assertEquals(SignalType.NO_TRADE_QUALITY_DEGRADED, signal.type());
        assertEquals("QUALITY_STATUS_DEGRADED", signal.attributes().get("qualityReason"));
        assertEquals("DEGRADED", signal.attributes().get("qualityStatus"));
        assertEquals("WARMING_UP", signal.attributes().get("qualityReasons"));
        assertEquals("true", signal.attributes().get("warmingUp"));
        assertFalse(signals.stream().anyMatch(s -> s.type() == SignalType.DATA_TRADABLE));
    }

    @Test
    void unsafeAndNoDataStatusAreNoTradeUnsafe() {
        for (FeatureQualityStatus status : List.of(FeatureQualityStatus.UNSAFE, FeatureQualityStatus.NO_DATA)) {
            FeatureQuality quality = cleanFlags()
                    .status(status)
                    .qualityReasons(List.of("BOOK_UNTRUSTED"))
                    .sourceOrderBookTrusted(false)
                    .sourceOrderBookReason("CROSSED_BOOK")
                    .build();

            List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(SignalRuleTestSupport.withQuality(quality)));

            assertEquals(1, signals.size(), status.name());
            MarketSignal signal = signals.getFirst();
            assertEquals(SignalType.NO_TRADE_QUALITY_UNSAFE, signal.type());
            assertEquals("QUALITY_STATUS_" + status.name(), signal.attributes().get("qualityReason"));
            assertEquals("BOOK_UNTRUSTED", signal.attributes().get("qualityReasons"));
            assertEquals("false", signal.attributes().get("sourceOrderBookTrusted"));
            assertEquals("CROSSED_BOOK", signal.attributes().get("sourceOrderBookReason"));
        }
    }

    @Test
    void missingStatusFailsClosedAsUnsafeNeverAsOk() {
        FeatureQuality quality = cleanFlags().status(null).build();

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(SignalRuleTestSupport.withQuality(quality)));

        assertEquals(1, signals.size());
        assertEquals(SignalType.NO_TRADE_QUALITY_UNSAFE, signals.getFirst().type());
        assertEquals("QUALITY_STATUS_MISSING", signals.getFirst().attributes().get("qualityReason"));
        assertEquals("MISSING", signals.getFirst().attributes().get("qualityStatus"));
    }

    @Test
    void degradedStatusAndStaleTradesEmitBothTypedReasons() {
        FeatureQuality quality = cleanFlags()
                .staleTrades(true)
                .tradeAgeMs(7_000L)
                .status(FeatureQualityStatus.DEGRADED)
                .qualityReasons(List.of("STALE_TRADES"))
                .build();

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(SignalRuleTestSupport.withQuality(quality)));

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_STALE_TRADES));
        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_QUALITY_DEGRADED));
        assertFalse(signals.stream().anyMatch(s -> s.type() == SignalType.DATA_TRADABLE));
    }

    @Test
    void okStatusWithCleanFlagsIsTradableAndCarriesV2Attributes() {
        FeatureQuality quality = cleanFlags()
                .status(FeatureQualityStatus.OK)
                .sourceOrderBookTrusted(true)
                .sourceOrderBookReason("NONE")
                .build();

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(SignalRuleTestSupport.withQuality(quality)));

        assertEquals(1, signals.size());
        MarketSignal signal = signals.getFirst();
        assertEquals(SignalType.DATA_TRADABLE, signal.type());
        assertEquals("OK", signal.attributes().get("qualityStatus"));
        assertEquals("false", signal.attributes().get("warmingUp"));
        assertEquals("false", signal.attributes().get("futureEventDetected"));
        assertEquals("true", signal.attributes().get("sourceOrderBookTrusted"));
        assertFalse(signal.attributes().containsKey("qualityReasons"));
    }

    private static FeatureQuality.FeatureQualityBuilder cleanFlags() {
        return FeatureQuality.builder()
                .syncStatus(SyncStatus.IN_SYNC)
                .staleOrderBookState(false)
                .staleTrades(false)
                .incompleteBook(false)
                .orderBookStateAgeMs(40L)
                .tradeAgeMs(90L);
    }

    private static MarketSignal first(List<MarketSignal> signals, SignalType type) {
        return signals.stream()
                .filter(s -> s.type() == type)
                .findFirst()
                .orElseThrow();
    }
}
