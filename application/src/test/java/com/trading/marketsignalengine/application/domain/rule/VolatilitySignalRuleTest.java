package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalDirection;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class VolatilitySignalRuleTest {

    private final VolatilitySignalRule rule = new VolatilitySignalRule();

    @Test
    void missingRegimeEmitsRiskOffNoTrade() {
        List<MarketSignal> signals = evaluate(featuresWithRegime(null));

        assertEquals(1, signals.size());
        MarketSignal signal = signals.getFirst();
        assertEquals(SignalType.NO_TRADE_VOLATILITY_MISSING, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertEquals(SignalStrength.STRONG, signal.strength());
        assertEquals(0, signal.confidence().compareTo(BigDecimal.ONE));
        assertFalse(signal.reason().contains("skeleton"));
        assertEquals("VOLATILITY_REGIME_MISSING", signal.attributes().get("riskReason"));
    }

    @Test
    void missingShortTermVolatilityEmitsRiskOffNoTrade() {
        List<MarketSignal> signals = evaluate(featuresWithRegime(
                RegimeFeature.builder().lastTradeDistanceToMidBps(new BigDecimal("1.0")).build()));

        assertEquals(1, signals.size());
        MarketSignal signal = signals.getFirst();
        assertEquals(SignalType.NO_TRADE_VOLATILITY_MISSING, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertFalse(signal.reason().contains("skeleton"));
        assertEquals("SHORT_TERM_VOLATILITY_1S_MISSING", signal.attributes().get("riskReason"));
    }

    @Test
    void volatilityEqualToThresholdEmitsVolatilityNormal() {
        // Default maxShortTermVolatility1s is 0.01; the boundary is inclusive (normal).
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("0.01")));

        assertEquals(SignalType.VOLATILITY_NORMAL, signal.type());
        assertEquals(SignalDirection.NEUTRAL, signal.direction());
    }

    @Test
    void volatilityBelowThresholdEmitsVolatilityNormal() {
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("0.005")));

        assertEquals(SignalType.VOLATILITY_NORMAL, signal.type());
        assertEquals(SignalDirection.NEUTRAL, signal.direction());
    }

    @Test
    void volatilityAboveThresholdEmitsVolatilityHigh() {
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("0.05")));

        assertEquals(SignalType.VOLATILITY_HIGH, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
    }

    @Test
    void volatilityNormalContainsLastTradeDistanceToMid() {
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("0.005")));

        assertEquals(SignalType.VOLATILITY_NORMAL, signal.type());
        assertEquals("0.005", signal.attributes().get("shortTermVolatility1s"));
        assertEquals("0.01", signal.attributes().get("maxShortTermVolatility1s"));
        assertEquals("1.0", signal.attributes().get("lastTradeDistanceToMidBps"));
    }

    @Test
    void negativeVolatilityProducesNoTradeInvalidVolatility() {
        // Negative volatility is an impossible feature value: no-trade, not VOLATILITY_NORMAL.
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("-0.01")));

        assertEquals(SignalType.NO_TRADE_INVALID_VOLATILITY, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertEquals("SHORT_TERM_VOLATILITY_1S_NEGATIVE", signal.attributes().get("riskReason"));
    }

    private MarketSignal evaluateFirst(MarketFeaturesSnapshot features) {
        return evaluate(features).getFirst();
    }

    private List<MarketSignal> evaluate(MarketFeaturesSnapshot features) {
        return rule.evaluate(SignalRuleTestSupport.context(features));
    }

    private static MarketFeaturesSnapshot volatility(BigDecimal shortTermVolatility1s) {
        return featuresWithRegime(RegimeFeature.builder()
                .shortTermVolatility1s(shortTermVolatility1s)
                .lastTradeDistanceToMidBps(new BigDecimal("1.0"))
                .build());
    }

    private static MarketFeaturesSnapshot featuresWithRegime(RegimeFeature regime) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .regime(regime)
                .build();
    }
}
