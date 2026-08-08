package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
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

    private static MarketSignal first(List<MarketSignal> signals, SignalType type) {
        return signals.stream()
                .filter(s -> s.type() == type)
                .findFirst()
                .orElseThrow();
    }
}
