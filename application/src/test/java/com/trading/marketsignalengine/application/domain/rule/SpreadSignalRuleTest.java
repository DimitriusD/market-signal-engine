package com.trading.marketsignalengine.application.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpreadSignalRuleTest {

    private final SpreadSignalRule rule = new SpreadSignalRule();

    @Test
    void spreadWithinThresholdEmitsSpreadAcceptable() {
        MarketFeaturesSnapshot features = featuresWithSpread(new BigDecimal("1.5"));

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.SPREAD_ACCEPTABLE, signals.getFirst().type());
    }

    @Test
    void spreadAboveThresholdEmitsSpreadTooWide() {
        MarketFeaturesSnapshot features = featuresWithSpread(new BigDecimal("5.0"));

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.SPREAD_TOO_WIDE, signals.getFirst().type());
    }

    @Test
    void missingSpreadEmitsSpreadTooWide() {
        MarketFeaturesSnapshot features = featuresWithSpread(null);

        List<MarketSignal> signals = rule.evaluate(SignalRuleTestSupport.context(features));

        assertEquals(SignalType.SPREAD_TOO_WIDE, signals.getFirst().type());
    }

    private static MarketFeaturesSnapshot featuresWithSpread(BigDecimal spreadBps) {
        return MarketFeaturesSnapshot.builder()
                .snapshotId("snap-1")
                .exchange("binance")
                .marketType("spot")
                .symbol("BTCUSDT")
                .instrumentId("binance:spot:BTCUSDT")
                .eventTime(Instant.parse("2026-01-01T00:00:00Z"))
                .receivedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .computedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .featureSetVersion("mfs-core-v1")
                .bbo(BboFeature.builder().spreadBps(spreadBps).build())
                .build();
    }
}
