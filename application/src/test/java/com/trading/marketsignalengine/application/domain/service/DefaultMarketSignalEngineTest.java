package com.trading.marketsignalengine.application.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.trading.marketsignalengine.application.domain.model.BboFeatureView;
import com.trading.marketsignalengine.application.domain.model.BookFeatureView;
import com.trading.marketsignalengine.application.domain.model.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.RegimeFeatureView;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.TradeFlowFeatureView;
import com.trading.marketsignalengine.application.domain.rule.CompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.DefaultCompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.OrderBookSignalRule;
import com.trading.marketsignalengine.application.domain.rule.QualitySignalRule;
import com.trading.marketsignalengine.application.domain.rule.RegimeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.SignalRule;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import com.trading.marketsignalengine.application.domain.rule.SpreadSignalRule;
import com.trading.marketsignalengine.application.domain.rule.TradeFlowSignalRule;
import com.trading.marketsignalengine.application.domain.rule.VolatilitySignalRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultMarketSignalEngineTest {

    private DefaultMarketSignalEngine engine;

    @BeforeEach
    void setUp() {
        List<SignalRule> baseRules = List.of(
                new QualitySignalRule(),
                new SpreadSignalRule(),
                new TradeFlowSignalRule(),
                new OrderBookSignalRule(),
                new VolatilitySignalRule(),
                new RegimeSignalRule());
        CompositeSignalRule compositeSignalRule = new DefaultCompositeSignalRule();
        engine = new DefaultMarketSignalEngine(baseRules, compositeSignalRule, new SignalAggregator());
    }

    @Test
    void validBullishFeaturesProduceBullishSnapshot() {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.defaultFeatures();
        SignalEvaluationContext context = SignalRuleTestSupport.context(features);

        MarketSignalSnapshot snapshot = engine.evaluate(context);

        assertEquals(MarketBias.BULLISH, snapshot.marketBias());
    }

    @Test
    void validBearishFeaturesProduceBearishSnapshot() {
        MarketFeaturesSnapshot features = MarketFeaturesSnapshot.builder()
                .snapshotId("snap-1")
                .exchange("binance")
                .marketType("spot")
                .symbol("BTCUSDT")
                .instrumentId("binance:spot:BTCUSDT")
                .eventTime(Instant.parse("2026-01-01T00:00:00Z"))
                .receivedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .computedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .featureSetVersion("mfs-core-v1")
                .quality(SignalRuleTestSupport.tradableQuality())
                .bbo(BboFeatureView.builder().spreadBps(new BigDecimal("1.0")).build())
                .book(BookFeatureView.builder().top5Imbalance(new BigDecimal("-0.80")).build())
                .tradeFlow(TradeFlowFeatureView.builder().signedTradeFlow5s(new BigDecimal("-100")).build())
                .regime(RegimeFeatureView.builder()
                        .shortTermVolatility1s(new BigDecimal("0.005"))
                        .lastTradeDistanceToMidBps(new BigDecimal("-1.0"))
                        .build())
                .build();
        SignalEvaluationContext context = SignalRuleTestSupport.context(features);

        MarketSignalSnapshot snapshot = engine.evaluate(context);

        assertEquals(MarketBias.BEARISH, snapshot.marketBias());
    }

    @Test
    void staleFeaturesProduceRiskOffSnapshot() {
        FeatureQuality quality = FeatureQuality.builder()
                .syncStatus(SyncStatus.OUT_OF_SYNC)
                .staleBbo(false)
                .staleBook(false)
                .staleTrades(false)
                .incompleteBook(false)
                .build();
        MarketFeaturesSnapshot features = MarketFeaturesSnapshot.builder()
                .snapshotId("snap-1")
                .exchange("binance")
                .marketType("spot")
                .symbol("BTCUSDT")
                .instrumentId("binance:spot:BTCUSDT")
                .eventTime(Instant.parse("2026-01-01T00:00:00Z"))
                .receivedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .computedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .featureSetVersion("mfs-core-v1")
                .quality(quality)
                .bbo(BboFeatureView.builder().spreadBps(new BigDecimal("10")).build())
                .build();
        SignalEvaluationContext context = SignalRuleTestSupport.context(features);

        MarketSignalSnapshot snapshot = engine.evaluate(context);

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());
    }
}
