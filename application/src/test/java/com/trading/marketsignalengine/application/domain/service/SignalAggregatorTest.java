package com.trading.marketsignalengine.application.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignalAggregatorTest {

    private final SignalAggregator aggregator = new SignalAggregator();

    @Test
    void mixedBiasEmitsMarketMixedDiagnostic() {
        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), List.of(
                bullish(SignalType.BUY_PRESSURE),
                bearish(SignalType.ORDER_BOOK_BEARISH)));

        assertEquals(MarketBias.MIXED, snapshot.marketBias());
        assertEquals(RiskLevel.ELEVATED, snapshot.riskLevel());
        assertTrue(snapshot.signals().stream().anyMatch(s -> s.type() == SignalType.MARKET_MIXED),
                "MIXED bias must be explained by a MARKET_MIXED diagnostic signal");
    }

    @Test
    void directionalBiasDoesNotEmitMarketMixed() {
        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), List.of(
                bullish(SignalType.BUY_PRESSURE),
                bullish(SignalType.ORDER_BOOK_BULLISH)));

        assertEquals(MarketBias.BULLISH, snapshot.marketBias());
        assertEquals(RiskLevel.NORMAL, snapshot.riskLevel());
        assertFalse(snapshot.signals().stream().anyMatch(s -> s.type() == SignalType.MARKET_MIXED));
    }

    @Test
    void riskOffYieldsNoTradeSnapshot() {
        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), List.of(
                riskOff(SignalType.NO_TRADE_CONDITION),
                bullish(SignalType.BUY_PRESSURE)));

        assertEquals(MarketBias.RISK_OFF, snapshot.marketBias());
        assertEquals(0, snapshot.marketBiasScore().signum());
        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());
        assertFalse(snapshot.signals().stream().anyMatch(s -> s.type() == SignalType.MARKET_MIXED));
    }

    @Test
    void doesNotMutateInputSignalList() {
        List<MarketSignal> input = List.of(
                bullish(SignalType.BUY_PRESSURE),
                bearish(SignalType.ORDER_BOOK_BEARISH));

        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), input);

        assertEquals(2, input.size());
        assertEquals(3, snapshot.signals().size());
    }

    @Test
    void publishedBiasAndScoreNeverContradict() {
        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), List.of(
                neutral(SignalType.DATA_TRADABLE),
                neutral(SignalType.SPREAD_ACCEPTABLE),
                bullish(SignalType.BUY_PRESSURE),
                neutral(SignalType.ORDER_BOOK_NEUTRAL),
                bullish(SignalType.REGIME_TRENDING_UP)));

        // The reported scenario: small positive score, but bias stays NEUTRAL and the two agree.
        assertEquals(MarketBias.NEUTRAL, snapshot.marketBias());
        assertTrue(snapshot.marketBiasScore().abs().compareTo(DirectionalReduction.DIRECTIONAL_THRESHOLD) < 0);
    }

    @Test
    void propagatesInstrumentIdentityFromFeatures() {
        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), List.of(
                bullish(SignalType.BUY_PRESSURE)));

        assertEquals("binance", snapshot.exchange());
        assertEquals("spot", snapshot.marketType());
        assertEquals("BTC", snapshot.base());
        assertEquals("USDT", snapshot.quote());
        assertEquals("BTCUSDT", snapshot.symbol());
        assertEquals("binance:spot:BTCUSDT", snapshot.instrumentId());
    }

    private static SignalEvaluationContext context() {
        return SignalRuleTestSupport.context(SignalRuleTestSupport.defaultFeatures());
    }

    private static MarketSignal bullish(SignalType type) {
        return MarketSignal.bullish(type, SignalStrength.STRONG, new BigDecimal("0.70"), "test", null);
    }

    private static MarketSignal bearish(SignalType type) {
        return MarketSignal.bearish(type, SignalStrength.STRONG, new BigDecimal("0.70"), "test", null);
    }

    private static MarketSignal neutral(SignalType type) {
        return MarketSignal.neutral(type, SignalStrength.NONE, new BigDecimal("0.50"), "test", null);
    }

    private static MarketSignal riskOff(SignalType type) {
        return MarketSignal.riskOff(type, SignalStrength.STRONG, BigDecimal.ONE, "test", null);
    }
}
