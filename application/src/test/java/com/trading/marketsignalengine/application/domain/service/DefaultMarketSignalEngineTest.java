package com.trading.marketsignalengine.application.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SetupSide;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalDirection;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.rule.DefaultCompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.OrderBookSignalRule;
import com.trading.marketsignalengine.application.domain.rule.QualitySignalRule;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import com.trading.marketsignalengine.application.domain.rule.SpreadSignalRule;
import com.trading.marketsignalengine.application.domain.rule.TradeFlowSignalRule;
import com.trading.marketsignalengine.application.domain.rule.VolatilitySignalRule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultMarketSignalEngineTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);

    private final DefaultMarketSignalEngine engine = new DefaultMarketSignalEngine(
            List.of(new QualitySignalRule()),
            List.of(new SpreadSignalRule(), new VolatilitySignalRule()),
            List.of(new TradeFlowSignalRule(), new OrderBookSignalRule()),
            new DefaultCompositeSignalRule(),
            new SignalAggregator(),
            SignalConfiguration.defaults(),
            FIXED_CLOCK);

    @Test
    void badQualitySuppressesDirectionalAndTradabilitySignals() {
        // Quality rejects the snapshot; directional data that would otherwise fire is present.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .quality(FeatureQuality.builder()
                        .syncStatus(SyncStatus.OUT_OF_SYNC)
                        .staleOrderBookState(true)
                        .build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(0, snapshot.marketBiasScore().signum());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.NO_TRADE_OUT_OF_SYNC));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        // Phase 3 never ran: no directional signals computed on data the quality gate rejected.
        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
        // Phase 2 never ran either, so no spread verdict on a quality-rejected (e.g. stale) BBO.
        assertFalse(has(snapshot, SignalType.SPREAD_ACCEPTABLE));
    }

    @Test
    void missingQualityProducesExactlyOneNoTradeCondition() {
        // quality == null is the gate's hardest rejection. The rule emits NO_TRADE_QUALITY_MISSING
        // (a RISK_OFF), which the engine detects and caps with its own single NO_TRADE_CONDITION.
        // Regression: the rule previously emitted NO_TRADE_CONDITION itself, so the published list
        // carried two of them and broke the "engine is the single emitter" invariant.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.withQuality(null);

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(0, snapshot.marketBiasScore().signum());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.NO_TRADE_QUALITY_MISSING));
        assertEquals(1, count(snapshot, SignalType.NO_TRADE_CONDITION));

        // Quality gate short-circuited: no tradability or directional signals computed.
        assertFalse(has(snapshot, SignalType.DATA_TRADABLE));
        assertFalse(has(snapshot, SignalType.SPREAD_ACCEPTABLE));
        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void wideSpreadSuppressesDirectionalSignals() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .bbo(BboFeature.builder().spreadBps(new BigDecimal("5.0")).build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.DATA_TRADABLE));
        assertTrue(has(snapshot, SignalType.SPREAD_TOO_WIDE));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void missingSpreadSuppressesDirectionalSignals() {
        // Missing spread data must short-circuit the tradability gate, not be read as SPREAD_TOO_WIDE.
        // Directional data that would otherwise fire is present in the snapshot.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .bbo(null)
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.NO_TRADE_SPREAD_MISSING));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        // Missing spread never produces a spread verdict.
        assertFalse(has(snapshot, SignalType.SPREAD_ACCEPTABLE));
        assertFalse(has(snapshot, SignalType.SPREAD_TOO_WIDE));

        // Phase 3 never ran: no directional or setup signals computed on no-trade data.
        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.SELL_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
        assertFalse(has(snapshot, SignalType.SHORT_SETUP_FORMING));
    }

    @Test
    void highVolatilitySuppressesDirectionalSignals() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .regime(RegimeFeature.builder()
                        .realizedVolatilityBps1s(new BigDecimal("120.0"))
                        .lastTradeDistanceToMidBps(new BigDecimal("1.0"))
                        .build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.VOLATILITY_HIGH));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void missingVolatilitySuppressesDirectionalSignals() {
        // Missing risk data must short-circuit the tradability gate, not be read as VOLATILITY_NORMAL.
        // Directional data that would otherwise fire is present in the snapshot.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .regime(null)
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.NO_TRADE_VOLATILITY_MISSING));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        // Missing volatility never produces VOLATILITY_NORMAL.
        assertFalse(has(snapshot, SignalType.VOLATILITY_NORMAL));

        // Phase 3 never ran: no directional or setup signals computed on no-trade data.
        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.SELL_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
        assertFalse(has(snapshot, SignalType.SHORT_SETUP_FORMING));
    }

    @Test
    void tradableSnapshotProducesDirectionalAndSetupSignals() {
        MarketSignalSnapshot snapshot = engine.evaluate(SignalRuleTestSupport.defaultFeatures());

        assertEquals(MarketBias.BULLISH, snapshot.marketBias());
        assertEquals(RiskLevel.NORMAL, snapshot.riskLevel());

        assertTrue(has(snapshot, SignalType.DATA_TRADABLE));
        assertTrue(has(snapshot, SignalType.SPREAD_ACCEPTABLE));
        assertTrue(has(snapshot, SignalType.VOLATILITY_NORMAL));
        assertTrue(has(snapshot, SignalType.BUY_PRESSURE));
        assertTrue(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertTrue(has(snapshot, SignalType.LONG_SETUP_FORMING));

        // Regime is no longer emitted as a signal: lastTradeDistanceToMidBps is not a trend.
        assertFalse(has(snapshot, SignalType.REGIME_TRENDING_UP));
        assertFalse(has(snapshot, SignalType.NO_TRADE_CONDITION));
    }

    @Test
    void reevaluatingSameSnapshotIsDeterministic() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.defaultFeatures();

        MarketSignalSnapshot first = engine.evaluate(features);
        MarketSignalSnapshot second = engine.evaluate(features);

        // A retried/duplicated input must produce the same identity and timestamp, so downstream
        // dedupes it instead of treating the replay as a brand-new signal event.
        assertEquals(first.signalSnapshotId(), second.signalSnapshotId());
        assertEquals(first.createdAt(), second.createdAt());
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), first.createdAt());
    }

    @Test
    void invalidBboProducesNoTradeRiskOffSnapshot() {
        // A crossed book (bid > ask) is an impossible BBO; the tradability gate must reject it.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .bbo(BboFeature.builder()
                        .bestBidPrice(new BigDecimal("101.0"))
                        .bestAskPrice(new BigDecimal("100.0"))
                        .build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());
        assertEquals(SetupSide.NONE, snapshot.setup().side());

        assertTrue(has(snapshot, SignalType.NO_TRADE_INVALID_BBO));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        assertFalse(has(snapshot, SignalType.SPREAD_ACCEPTABLE));
        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void directionalEvidenceEmittedBeforeAPhase3RiskOffIsNotPublished() {
        // TradeFlowSignalRule (runs first) emits BUY_PRESSURE; OrderBookSignalRule (runs second)
        // then detects an out-of-range top5Imbalance and goes RISK_OFF. The published no-trade
        // snapshot must not carry the already-emitted directional evidence: a no-trade snapshot
        // never contains bullish/bearish signals.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .book(BookFeature.builder()
                        .levelsUsed(5)
                        .top5Imbalance(new BigDecimal("2.0"))
                        .build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(0, snapshot.marketBiasScore().signum());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());
        assertEquals(SetupSide.NONE, snapshot.setup().side());

        assertTrue(has(snapshot, SignalType.NO_TRADE_INVALID_ORDER_BOOK));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.SELL_PRESSURE));
        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));

        // The invariant holds for every published signal, not just known types.
        assertTrue(snapshot.signals().stream()
                .noneMatch(signal -> signal.direction() == SignalDirection.BULLISH
                        || signal.direction() == SignalDirection.BEARISH));
    }

    @Test
    void invalidVolatilityProducesNoTradeRiskOffSnapshot() {
        // Negative volatility is an impossible feature value; the tradability gate must reject it.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .regime(RegimeFeature.builder()
                        .realizedVolatilityBps1s(new BigDecimal("-1.0"))
                        .lastTradeDistanceToMidBps(new BigDecimal("1.0"))
                        .build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());
        assertEquals(SetupSide.NONE, snapshot.setup().side());

        assertTrue(has(snapshot, SignalType.NO_TRADE_INVALID_VOLATILITY));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        assertFalse(has(snapshot, SignalType.VOLATILITY_NORMAL));
        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
    }

    @Test
    void invalidTradeFlowShortCircuitsBeforeCompositeSetup() {
        // Out-of-range imbalance is a Phase-3 RISK_OFF; no composite setup must form on garbage input.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(TradeFlowFeature.builder()
                        .signedFlowImbalance5s(new BigDecimal("1.42"))
                        .tradeCount5s(50)
                        .build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());
        assertEquals(SetupSide.NONE, snapshot.setup().side());

        assertTrue(has(snapshot, SignalType.NO_TRADE_INVALID_TRADE_FLOW));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        assertFalse(has(snapshot, SignalType.BUY_PRESSURE));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
        assertFalse(has(snapshot, SignalType.SHORT_SETUP_FORMING));
    }

    @Test
    void invalidOrderBookShortCircuitsBeforeCompositeSetup() {
        // Out-of-range top5Imbalance is a Phase-3 RISK_OFF; no composite setup must form on garbage input.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .book(BookFeature.builder()
                        .levelsUsed(5)
                        .top5Imbalance(new BigDecimal("1.5"))
                        .build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());
        assertEquals(SetupSide.NONE, snapshot.setup().side());

        assertTrue(has(snapshot, SignalType.NO_TRADE_INVALID_ORDER_BOOK));
        assertTrue(has(snapshot, SignalType.NO_TRADE_CONDITION));

        assertFalse(has(snapshot, SignalType.ORDER_BOOK_BULLISH));
        assertFalse(has(snapshot, SignalType.LONG_SETUP_FORMING));
        assertFalse(has(snapshot, SignalType.SHORT_SETUP_FORMING));
    }

    @Test
    void noTradeConditionContainsRiskOffSignalList() {
        // A wide spread is the active risk-off gate; the engine's NO_TRADE_CONDITION must name it.
        MarketFeaturesSnapshot features = SignalRuleTestSupport.tradableFeaturesBuilder()
                .bbo(BboFeature.builder().spreadBps(new BigDecimal("5.0")).build())
                .build();

        MarketSignalSnapshot snapshot = engine.evaluate(features);

        MarketSignal noTrade = snapshot.signals().stream()
                .filter(signal -> signal.type() == SignalType.NO_TRADE_CONDITION)
                .findFirst()
                .orElseThrow();

        assertEquals("NO_TRADE", noTrade.attributes().get("condition"));
        assertEquals("ONE_OR_MORE_RISK_OFF_SIGNALS_ACTIVE", noTrade.attributes().get("reason"));
        assertEquals("DefaultMarketSignalEngine", noTrade.attributes().get("emittedBy"));
        assertTrue(noTrade.attributes().get("riskOffSignals").contains(SignalType.SPREAD_TOO_WIDE.name()));
    }

    private static boolean has(MarketSignalSnapshot snapshot, SignalType type) {
        return snapshot.signals().stream().anyMatch(signal -> signal.type() == type);
    }

    private static long count(MarketSignalSnapshot snapshot, SignalType type) {
        return snapshot.signals().stream().filter(signal -> signal.type() == type).count();
    }
}
