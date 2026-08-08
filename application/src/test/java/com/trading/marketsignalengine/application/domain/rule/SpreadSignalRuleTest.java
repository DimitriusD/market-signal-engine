package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalDirection;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpreadSignalRuleTest {

    private final SpreadSignalRule rule = new SpreadSignalRule();

    @Test
    void missingBboEmitsRiskOffNoTradeSpreadMissing() {
        // A null BBO is missing data, not a wide spread: it must not be read as SPREAD_TOO_WIDE.
        List<MarketSignal> signals = evaluate(featuresWithBbo(null));

        assertEquals(1, signals.size());
        MarketSignal signal = signals.getFirst();
        assertEquals(SignalType.NO_TRADE_SPREAD_MISSING, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertEquals(SignalStrength.STRONG, signal.strength());
        assertEquals(0, signal.confidence().compareTo(BigDecimal.ONE));
        assertEquals("BBO_MISSING", signal.attributes().get("riskReason"));
    }

    @Test
    void missingSpreadBpsEmitsRiskOffNoTradeSpreadMissing() {
        // A populated BBO with a null spreadBps is still missing data, not a wide spread.
        List<MarketSignal> signals = evaluate(featuresWithBbo(BboFeature.builder()
                .bestBidPrice(new BigDecimal("100.0"))
                .bestAskPrice(new BigDecimal("100.2"))
                .spreadAbs(new BigDecimal("0.2"))
                .spreadBps(null)
                .build()));

        assertEquals(1, signals.size());
        MarketSignal signal = signals.getFirst();
        assertEquals(SignalType.NO_TRADE_SPREAD_MISSING, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertEquals("SPREAD_BPS_MISSING", signal.attributes().get("riskReason"));
    }

    @Test
    void spreadEqualToThresholdEmitsSpreadAcceptable() {
        // Default maxSpreadBps is 2.0; the boundary is inclusive (acceptable).
        MarketSignal signal = evaluateFirst(spread(new BigDecimal("2.0")));

        assertEquals(SignalType.SPREAD_ACCEPTABLE, signal.type());
        assertEquals(SignalDirection.NEUTRAL, signal.direction());
    }

    @Test
    void spreadBelowThresholdEmitsSpreadAcceptable() {
        MarketSignal signal = evaluateFirst(spread(new BigDecimal("1.5")));

        assertEquals(SignalType.SPREAD_ACCEPTABLE, signal.type());
        assertEquals(SignalDirection.NEUTRAL, signal.direction());
    }

    @Test
    void spreadAboveThresholdEmitsSpreadTooWide() {
        MarketSignal signal = evaluateFirst(spread(new BigDecimal("5.0")));

        assertEquals(SignalType.SPREAD_TOO_WIDE, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertFalse(signal.reason().contains("missing"));
        assertEquals("SPREAD_BPS_ABOVE_THRESHOLD", signal.attributes().get("riskReason"));
    }

    @Test
    void spreadAcceptableContainsMidAndMicropriceAttributes() {
        MarketSignal signal = evaluateFirst(featuresWithBbo(BboFeature.builder()
                .spreadBps(new BigDecimal("1.0"))
                .spreadAbs(new BigDecimal("0.2"))
                .bestBidPrice(new BigDecimal("100.0"))
                .bestAskPrice(new BigDecimal("100.2"))
                .bestBidQty(new BigDecimal("12.5"))
                .bestAskQty(new BigDecimal("8.0"))
                .midPrice(new BigDecimal("100.1"))
                .micropriceTop1(new BigDecimal("100.12"))
                .micropriceOffsetBps(new BigDecimal("2.0"))
                .build()));

        assertEquals(SignalType.SPREAD_ACCEPTABLE, signal.type());
        assertEquals("1.0", signal.attributes().get("spreadBps"));
        assertEquals("2.0", signal.attributes().get("maxSpreadBps"));
        assertEquals("100.1", signal.attributes().get("midPrice"));
        assertEquals("100.12", signal.attributes().get("micropriceTop1"));
        assertEquals("2.0", signal.attributes().get("micropriceOffsetBps"));
        assertEquals("12.5", signal.attributes().get("bestBidQty"));
        assertEquals("8.0", signal.attributes().get("bestAskQty"));
    }

    @Test
    void crossedBboProducesNoTradeInvalidBbo() {
        // bestBidPrice > bestAskPrice is an impossible book: no-trade, not SPREAD_ACCEPTABLE.
        MarketSignal signal = evaluateFirst(featuresWithBbo(BboFeature.builder()
                .bestBidPrice(new BigDecimal("101.0"))
                .bestAskPrice(new BigDecimal("100.0"))
                .build()));

        assertEquals(SignalType.NO_TRADE_INVALID_BBO, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertEquals("CROSSED_BBO", signal.attributes().get("riskReason"));
    }

    @Test
    void negativeSpreadBpsProducesNoTradeInvalidBbo() {
        MarketSignal signal = evaluateFirst(featuresWithBbo(BboFeature.builder()
                .spreadBps(new BigDecimal("-1.0"))
                .build()));

        assertEquals(SignalType.NO_TRADE_INVALID_BBO, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertEquals("NEGATIVE_SPREAD_BPS", signal.attributes().get("riskReason"));
    }

    @Test
    void invalidBidAskProducesNoTradeInvalidBbo() {
        MarketSignal signal = evaluateFirst(featuresWithBbo(BboFeature.builder()
                .bestBidPrice(new BigDecimal("-1.0"))
                .bestAskPrice(new BigDecimal("100.0"))
                .spreadBps(new BigDecimal("1.0"))
                .build()));

        assertEquals(SignalType.NO_TRADE_INVALID_BBO, signal.type());
        assertEquals(SignalDirection.RISK_OFF, signal.direction());
        assertEquals("BEST_BID_PRICE_INVALID", signal.attributes().get("riskReason"));
    }

    private MarketSignal evaluateFirst(MarketFeaturesSnapshot features) {
        return evaluate(features).getFirst();
    }

    private List<MarketSignal> evaluate(MarketFeaturesSnapshot features) {
        return rule.evaluate(SignalRuleTestSupport.context(features));
    }

    private static MarketFeaturesSnapshot spread(BigDecimal spreadBps) {
        return featuresWithBbo(BboFeature.builder().spreadBps(spreadBps).build());
    }

    private static MarketFeaturesSnapshot featuresWithBbo(BboFeature bbo) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .bbo(bbo)
                .build();
    }
}
