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
    void missingRealizedVolatilityEmitsRiskOffNoTrade() {
        List<MarketSignal> signals = evaluate(featuresWithRegime(
                RegimeFeature.builder().lastTradeDistanceToMidBps(new BigDecimal("1.0")).build()));

        assertEquals(1, signals.size());
        MarketSignal signal = signals.getFirst();
        assertEquals(SignalType.NO_TRADE_VOLATILITY_MISSING, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertFalse(signal.reason().contains("skeleton"));
        assertEquals("REALIZED_VOLATILITY_BPS_1S_MISSING", signal.attributes().get("riskReason"));
    }

    @Test
    void volatilityEqualToThresholdEmitsVolatilityNormal() {
        // Default maxRealizedVolatilityBps1s is 50.0 bps; the boundary is inclusive (normal).
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("50.0")));

        assertEquals(SignalType.VOLATILITY_NORMAL, signal.type());
        assertEquals(SignalDirection.NEUTRAL, signal.direction());
    }

    @Test
    void volatilityBelowThresholdEmitsVolatilityNormal() {
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("5.0")));

        assertEquals(SignalType.VOLATILITY_NORMAL, signal.type());
        assertEquals(SignalDirection.NEUTRAL, signal.direction());
    }

    @Test
    void volatilityAboveThresholdEmitsVolatilityHigh() {
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("120.0")));

        assertEquals(SignalType.VOLATILITY_HIGH, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertEquals("REALIZED_VOLATILITY_BPS_1S_ABOVE_THRESHOLD", signal.attributes().get("riskReason"));
    }

    @Test
    void volatilityNormalContainsObservedValuesAndCalibrationMarker() {
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("5.0")));

        assertEquals(SignalType.VOLATILITY_NORMAL, signal.type());
        assertEquals("5.0", signal.attributes().get("realizedVolatilityBps1s"));
        assertEquals("50.0", signal.attributes().get("maxRealizedVolatilityBps1s"));
        assertEquals("UNCALIBRATED", signal.attributes().get("volatilityThresholdCalibration"));
        assertEquals("1.0", signal.attributes().get("lastTradeDistanceToMidBps"));
    }

    @Test
    void everyVolatilitySignalCarriesUncalibratedMarkerUntilReplayCalibration() {
        assertEquals("UNCALIBRATED",
                evaluateFirst(featuresWithRegime(null)).attributes().get("volatilityThresholdCalibration"));
        assertEquals("UNCALIBRATED",
                evaluateFirst(volatility(new BigDecimal("120.0"))).attributes().get("volatilityThresholdCalibration"));
    }

    @Test
    void negativeVolatilityProducesNoTradeInvalidVolatility() {
        // Negative realized volatility is an impossible feature value: no-trade, not VOLATILITY_NORMAL.
        MarketSignal signal = evaluateFirst(volatility(new BigDecimal("-1.0")));

        assertEquals(SignalType.NO_TRADE_INVALID_VOLATILITY, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertEquals("REALIZED_VOLATILITY_BPS_1S_NEGATIVE", signal.attributes().get("riskReason"));
    }

    private MarketSignal evaluateFirst(MarketFeaturesSnapshot features) {
        return evaluate(features).getFirst();
    }

    private List<MarketSignal> evaluate(MarketFeaturesSnapshot features) {
        return rule.evaluate(SignalRuleTestSupport.context(features));
    }

    private static MarketFeaturesSnapshot volatility(BigDecimal realizedVolatilityBps1s) {
        return featuresWithRegime(RegimeFeature.builder()
                .realizedVolatilityBps1s(realizedVolatilityBps1s)
                .lastTradeDistanceToMidBps(new BigDecimal("1.0"))
                .build());
    }

    private static MarketFeaturesSnapshot featuresWithRegime(RegimeFeature regime) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .regime(regime)
                .build();
    }
}
