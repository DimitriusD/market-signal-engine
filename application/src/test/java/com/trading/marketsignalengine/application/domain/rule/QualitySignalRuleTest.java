package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
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
    void outOfSyncQualityEmitsNoTradeOutOfSync() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.OUT_OF_SYNC)
                .staleBbo(false)
                .staleBook(false)
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
                .staleBbo(false)
                .staleBook(false)
                .staleTrades(false)
                .incompleteBook(false)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_STALE_BOOK));
    }

    @Test
    void recoveringBookEmitsNoTradeRecoveringBook() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.RECOVERING)
                .staleBbo(false)
                .staleBook(false)
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
                .staleBbo(false)
                .staleBook(false)
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
                .staleBbo(false)
                .staleBook(false)
                .staleTrades(false)
                .incompleteBook(true)
                .build();
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(quality);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.NO_TRADE_INCOMPLETE_BOOK));
    }
}
