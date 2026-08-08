package com.trading.marketsignalengine.application.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SetupSide;
import com.trading.marketsignalengine.application.domain.model.SetupType;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
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
    void marketMixedContainsDiagnosticAttributes() {
        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), List.of(
                bullish(SignalType.BUY_PRESSURE),
                bearish(SignalType.ORDER_BOOK_BEARISH)));

        MarketSignal marketMixed = snapshot.signals().stream()
                .filter(s -> s.type() == SignalType.MARKET_MIXED)
                .findFirst()
                .orElseThrow();

        assertEquals("BULLISH_AND_BEARISH_BASE_SIGNALS_CONFLICT",
                marketMixed.attributes().get("diagnosticReason"));
        assertEquals("true", marketMixed.attributes().get("hasBullishBase"));
        assertEquals("true", marketMixed.attributes().get("hasBearishBase"));
        assertEquals(snapshot.marketBiasScore().toPlainString(),
                marketMixed.attributes().get("marketBiasScore"));
        assertEquals(DirectionalReduction.DIRECTIONAL_THRESHOLD.toPlainString(),
                marketMixed.attributes().get("directionalThreshold"));
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

    @Test
    void signalSnapshotIdIsDeterministicForSameSourceAndVersion() {
        MarketSignalSnapshot first = aggregator.aggregate(context(), List.of(bullish(SignalType.BUY_PRESSURE)));
        MarketSignalSnapshot second = aggregator.aggregate(context(), List.of(bullish(SignalType.BUY_PRESSURE)));

        // Same source snapshot + same signal-set version => same id, so retries dedupe downstream.
        assertEquals(first.signalSnapshotId(), second.signalSnapshotId());
    }

    @Test
    void signalSnapshotIdChangesWithSignalSetVersion() {
        MarketSignalSnapshot v1 = aggregator.aggregate(contextWithVersion("mse-signals-v1"),
                List.of(bullish(SignalType.BUY_PRESSURE)));
        MarketSignalSnapshot v2 = aggregator.aggregate(contextWithVersion("mse-signals-v2"),
                List.of(bullish(SignalType.BUY_PRESSURE)));

        // A signal-logic version bump is a deliberate cache-bust: same input, new id.
        assertNotEquals(v1.signalSnapshotId(), v2.signalSnapshotId());
    }

    @Test
    void aggregateLongSetupContainsSetupAndValidity() {
        // A LONG_SETUP_FORMING signal (as the composite rule would emit) drives the snapshot setup.
        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), List.of(
                bullish(SignalType.BUY_PRESSURE),
                bullish(SignalType.ORDER_BOOK_BULLISH),
                MarketSignal.bullish(SignalType.LONG_SETUP_FORMING, SignalStrength.STRONG,
                        new BigDecimal("0.75"), "long setup", null)));

        assertEquals(SetupSide.LONG, snapshot.setup().side());
        assertEquals(SetupType.MICROSTRUCTURE_MOMENTUM, snapshot.setup().type());
        assertEquals(SignalStrength.STRONG, snapshot.setup().strength());
        assertEquals(0, snapshot.setup().confidence().compareTo(new BigDecimal("0.75")));
        assertEquals(SignalConfiguration.defaults().microstructureSetupTtlMs(), snapshot.ttlMs());
        assertEquals(EVALUATED_AT.plusMillis(snapshot.ttlMs()), snapshot.validUntil());
    }

    @Test
    void aggregateNoTradeContainsNoneSetupAndRiskOffValidity() {
        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), List.of(
                riskOff(SignalType.NO_TRADE_CONDITION),
                bullish(SignalType.BUY_PRESSURE)));

        assertEquals(RiskLevel.NO_TRADE, snapshot.riskLevel());
        assertEquals(SetupSide.NONE, snapshot.setup().side());
        assertEquals(SignalConfiguration.defaults().riskOffTtlMs(), snapshot.ttlMs());
        assertEquals(EVALUATED_AT.plusMillis(snapshot.ttlMs()), snapshot.validUntil());
    }

    @Test
    void aggregateNeutralContainsNoneSetupAndNeutralValidity() {
        MarketSignalSnapshot snapshot = aggregator.aggregate(context(), List.of(
                neutral(SignalType.DATA_TRADABLE),
                neutral(SignalType.SPREAD_ACCEPTABLE)));

        assertEquals(RiskLevel.NORMAL, snapshot.riskLevel());
        assertEquals(SetupSide.NONE, snapshot.setup().side());
        assertEquals(SignalConfiguration.defaults().neutralTtlMs(), snapshot.ttlMs());
        assertEquals(EVALUATED_AT.plusMillis(snapshot.ttlMs()), snapshot.validUntil());
    }

    private static final Instant EVALUATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static SignalEvaluationContext context() {
        return SignalRuleTestSupport.context(SignalRuleTestSupport.defaultFeatures());
    }

    private static SignalEvaluationContext contextWithVersion(String signalSetVersion) {
        MarketFeaturesSnapshot features = SignalRuleTestSupport.defaultFeatures();
        return new SignalEvaluationContext(
                features,
                SignalConfiguration.defaults().toBuilder().signalSetVersion(signalSetVersion).build(),
                Instant.parse("2026-01-01T00:00:00Z"));
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
