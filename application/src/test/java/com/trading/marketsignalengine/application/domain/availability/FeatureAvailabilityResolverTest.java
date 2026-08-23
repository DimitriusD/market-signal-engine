package com.trading.marketsignalengine.application.domain.availability;

import static com.trading.marketsignalengine.application.domain.availability.FeatureAvailabilityStatus.AVAILABLE;
import static com.trading.marketsignalengine.application.domain.availability.FeatureAvailabilityStatus.FAILED;
import static com.trading.marketsignalengine.application.domain.availability.FeatureAvailabilityStatus.UNAVAILABLE;
import static com.trading.marketsignalengine.application.domain.availability.FeatureAvailabilityStatus.UNTRUSTED;
import static com.trading.marketsignalengine.application.domain.availability.FeatureAvailabilityStatus.WARMING_UP;
import static com.trading.marketsignalengine.application.domain.availability.FeatureWindowHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.availability.FeatureWindowHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.availability.FeatureWindowHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.availability.FeatureWindowHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Input-side availability of the 1S/5S/15S/60S trade-flow windows. The wire facts under test:
 * null ≠ zero; 15S/60S counters are nullable (null = not computed, 0 = measured empty window);
 * 1S/5S counters default to 0 on the wire so only computed nullable metrics or a positive count
 * prove the window exists; failures / staleness / gaps / warm-up have a fixed precedence.
 */
class FeatureAvailabilityResolverTest {

    private final FeatureAvailabilityResolver resolver = new FeatureAvailabilityResolver();

    // ------------------------------------------------------------------ populated / zero values

    @Test
    void populatedWindowsAreAvailableOnEveryHorizon() {
        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(
                tradeFlow(populated(9), populated(50), populated(150), populated(600))));

        for (FeatureWindowHorizon horizon : FeatureWindowHorizon.values()) {
            assertEquals(AVAILABLE, availability.statusOf(horizon), horizon.label());
            assertTrue(availability.isAvailable(horizon));
            assertEquals(List.of(FeatureAvailabilityResolver.CODE_WINDOW_COMPUTED),
                    availability.of(horizon).reasonCodes());
        }
    }

    @Test
    void coveredEmptyWindowWithCountZeroIsAvailable() {
        // 15S/60S: count 0 (non-null) is a measured empty window. 1S/5S: a computed metric carrying a
        // real zero (e.g. tradeIntensity 0) proves the window was computed although counts are 0.
        TradeFlowWindow coveredEmptyShort = TradeFlowWindow.builder()
                .tradeCount(0).validQtyTradeCount(0).aggressiveTradeCount(0).unknownSideCount(0)
                .tradeIntensity(BigDecimal.ZERO)
                .build();
        TradeFlowWindow coveredEmptyLong = TradeFlowWindow.builder()
                .tradeCount(0).validQtyTradeCount(0).aggressiveTradeCount(0).unknownSideCount(0)
                .build();

        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(
                tradeFlow(coveredEmptyShort, coveredEmptyShort, coveredEmptyLong, coveredEmptyLong)));

        for (FeatureWindowHorizon horizon : FeatureWindowHorizon.values()) {
            assertEquals(AVAILABLE, availability.statusOf(horizon), horizon.label());
        }
    }

    @Test
    void zeroImbalanceAndZeroVolumeAreValuesNotAbsence() {
        TradeFlowWindow zeroFlow = TradeFlowWindow.builder()
                .buyAggressiveVolume(BigDecimal.ZERO)
                .sellAggressiveVolume(BigDecimal.ZERO)
                .totalAggressiveVolume(BigDecimal.ZERO)
                .signedTradeFlow(BigDecimal.ZERO)
                .signedFlowImbalance(BigDecimal.ZERO)
                .tradeCount(0)
                .build();

        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(
                tradeFlow(zeroFlow, zeroFlow, zeroFlow, zeroFlow)));

        for (FeatureWindowHorizon horizon : FeatureWindowHorizon.values()) {
            assertEquals(AVAILABLE, availability.statusOf(horizon), horizon.label());
        }
    }

    // ------------------------------------------------------------------ absent windows

    @Test
    void absentLongWindowsWithNullCountsAreUnavailableWithoutWarmUp() {
        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(
                tradeFlow(populated(9), populated(50), TradeFlowWindow.builder().build(), null)));

        assertEquals(AVAILABLE, availability.statusOf(H1S));
        assertEquals(AVAILABLE, availability.statusOf(H5S));
        assertEquals(UNAVAILABLE, availability.statusOf(H15S));
        assertEquals(UNAVAILABLE, availability.statusOf(H60S));
        assertEquals(List.of(FeatureAvailabilityResolver.CODE_WINDOW_NOT_COMPUTED),
                availability.of(H60S).reasonCodes());
    }

    @Test
    void absentLongWindowsDuringWarmUpAreWarmingUp() {
        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(
                tradeFlow(populated(9), populated(50), TradeFlowWindow.builder().build(), null),
                warmingUpQuality()));

        assertEquals(WARMING_UP, availability.statusOf(H15S));
        assertEquals(WARMING_UP, availability.statusOf(H60S));
    }

    @Test
    void absentShortWindowsWithDefaultZeroCountsAndNoComputedMarkersAreNotAvailable() {
        // Wire defaults for 1S/5S: all counters 0, every nullable metric null — nothing proves
        // the window was computed, so it must not be read as "available and empty".
        TradeFlowWindow wireDefault = TradeFlowWindow.builder()
                .tradeCount(0).validQtyTradeCount(0).aggressiveTradeCount(0).unknownSideCount(0)
                .build();

        TradeFlowAvailability plain = resolver.resolveTradeFlow(snapshot(
                tradeFlow(wireDefault, wireDefault, null, null)));
        TradeFlowAvailability warming = resolver.resolveTradeFlow(snapshot(
                tradeFlow(wireDefault, wireDefault, null, null), warmingUpQuality()));

        assertEquals(UNAVAILABLE, plain.statusOf(H1S));
        assertEquals(UNAVAILABLE, plain.statusOf(H5S));
        assertEquals(WARMING_UP, warming.statusOf(H1S));
        assertEquals(WARMING_UP, warming.statusOf(H5S));
        assertFalse(plain.isAvailable(H1S));
        assertFalse(warming.isAvailable(H5S));
    }

    @Test
    void positiveCountAloneProvesAShortWindowWasComputed() {
        TradeFlowWindow countsOnly = TradeFlowWindow.builder().tradeCount(3).build();

        assertEquals(AVAILABLE, resolver.resolveTradeFlow(snapshot(
                tradeFlow(countsOnly, countsOnly, null, null)), H1S).status());
        assertTrue(FeatureAvailabilityResolver.isComputed(countsOnly, H5S));
        assertFalse(FeatureAvailabilityResolver.isComputed(TradeFlowWindow.builder().tradeCount(0).build(), H5S));
        assertTrue(FeatureAvailabilityResolver.isComputed(TradeFlowWindow.builder().tradeCount(0).build(), H15S));
        assertFalse(FeatureAvailabilityResolver.isComputed(TradeFlowWindow.builder().build(), H60S));
        assertFalse(FeatureAvailabilityResolver.isComputed(null, H1S));
    }

    @Test
    void globalWarmUpKeepsCoveredShortWindowsAvailableWhileLongWindowsWarmUp() {
        // First trade 10s ago: 1S/5S are covered and computed, 15S/60S are not yet.
        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(
                tradeFlow(populated(4), populated(20), null, null), warmingUpQuality()));

        assertEquals(AVAILABLE, availability.statusOf(H1S));
        assertEquals(AVAILABLE, availability.statusOf(H5S));
        assertEquals(WARMING_UP, availability.statusOf(H15S));
        assertEquals(WARMING_UP, availability.statusOf(H60S));
    }

    @Test
    void absentTradeFlowGroupIsUnavailableUnlessFailed() {
        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(null));

        for (FeatureWindowHorizon horizon : FeatureWindowHorizon.values()) {
            assertEquals(UNAVAILABLE, availability.statusOf(horizon), horizon.label());
            assertEquals(List.of(FeatureAvailabilityResolver.CODE_GROUP_ABSENT), availability.of(horizon).reasonCodes());
        }
    }

    // ------------------------------------------------------------------ failure / trust

    @Test
    void tradeFlowCalculatorFailureMakesEveryHorizonFailed() {
        MarketFeaturesSnapshot snapshot = snapshot(
                tradeFlow(populated(9), populated(50), populated(150), populated(600)),
                SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .staleTrades(true)
                        .warmingUp(true)
                        .qualityReasons(List.of("CALCULATOR_FAILURE", "STALE_TRADES", "WARMING_UP"))
                        .build())
                .toBuilder()
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("trade-flow")).totalFeatureGroups(4).build())
                .build();

        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot);

        for (FeatureWindowHorizon horizon : FeatureWindowHorizon.values()) {
            assertEquals(FAILED, availability.statusOf(horizon), "FAILED takes precedence on " + horizon.label());
            assertEquals(List.of(FeatureAvailabilityResolver.CODE_CALCULATOR_FAILED),
                    availability.of(horizon).reasonCodes());
        }
    }

    @Test
    void otherGroupFailuresDoNotFailTradeFlow() {
        MarketFeaturesSnapshot snapshot = snapshot(
                tradeFlow(populated(9), populated(50), populated(150), populated(600)))
                .toBuilder()
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("bbo", "order-book", "short-term-regime")).totalFeatureGroups(4).build())
                .build();

        assertEquals(AVAILABLE, resolver.resolveTradeFlow(snapshot, H5S).status());
    }

    @Test
    void staleTradesMakeEveryHorizonUntrusted() {
        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(
                tradeFlow(populated(9), populated(50), populated(150), populated(600)),
                SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .staleTrades(true)
                        .warmingUp(true)
                        .qualityReasons(List.of("STALE_TRADES", "WARMING_UP"))
                        .build()));

        for (FeatureWindowHorizon horizon : FeatureWindowHorizon.values()) {
            assertEquals(UNTRUSTED, availability.statusOf(horizon), horizon.label());
            assertEquals(List.of(FeatureAvailabilityResolver.CODE_STALE_TRADES), availability.of(horizon).reasonCodes());
        }
    }

    @Test
    void tradeHistoryGapMakesTheUncoveredHorizonsUntrusted() {
        // MFS v2 leaves exactly the windows spanning the gap uncovered and computes the rest from
        // complete history: computed windows stay AVAILABLE, uncovered ones are UNTRUSTED (not
        // WARMING_UP even if warm-up is also flagged, not UNAVAILABLE).
        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(
                tradeFlow(populated(9), populated(50), null, null),
                SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .warmingUp(true)
                        .qualityReasons(List.of("TRADE_HISTORY_GAP", "WARMING_UP"))
                        .build()));

        assertEquals(AVAILABLE, availability.statusOf(H1S));
        assertEquals(AVAILABLE, availability.statusOf(H5S));
        assertEquals(UNTRUSTED, availability.statusOf(H15S));
        assertEquals(UNTRUSTED, availability.statusOf(H60S));
        assertEquals(List.of(FeatureAvailabilityResolver.CODE_TRADE_HISTORY_GAP), availability.of(H60S).reasonCodes());
    }

    @Test
    void untrustedOrderBookDoesNotAffectTradeFlowAvailability() {
        TradeFlowAvailability availability = resolver.resolveTradeFlow(snapshot(
                tradeFlow(populated(9), populated(50), populated(150), populated(600)),
                SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.UNSAFE)
                        .sourceOrderBookTrusted(false)
                        .qualityReasons(List.of("BOOK_UNTRUSTED"))
                        .build()));

        for (FeatureWindowHorizon horizon : FeatureWindowHorizon.values()) {
            assertEquals(AVAILABLE, availability.statusOf(horizon), horizon.label());
        }
    }

    // ------------------------------------------------------------------ determinism / immutability

    @Test
    void resolverIsDeterministicAndResultIsImmutable() {
        MarketFeaturesSnapshot snapshot = snapshot(
                tradeFlow(populated(9), populated(50), null, null), warmingUpQuality());

        TradeFlowAvailability first = resolver.resolveTradeFlow(snapshot);
        TradeFlowAvailability second = new FeatureAvailabilityResolver().resolveTradeFlow(snapshot);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(List.of(H1S, H5S, H15S, H60S), List.copyOf(first.asMap().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> first.asMap().put(H1S, null));
        assertThrows(UnsupportedOperationException.class,
                () -> first.of(H1S).reasonCodes().add("X"));
    }

    @Test
    void tradeFlowAvailabilityRequiresEveryHorizon() {
        assertThrows(IllegalArgumentException.class, () -> new TradeFlowAvailability(Map.of(
                H1S, FeatureWindowAvailability.of(H1S, AVAILABLE))));
        assertThrows(IllegalArgumentException.class, () -> new TradeFlowAvailability(Map.of(
                H1S, FeatureWindowAvailability.of(H5S, AVAILABLE),
                H5S, FeatureWindowAvailability.of(H5S, AVAILABLE),
                H15S, FeatureWindowAvailability.of(H15S, AVAILABLE),
                H60S, FeatureWindowAvailability.of(H60S, AVAILABLE))));
    }

    // ------------------------------------------------------------------ fixtures

    private static MarketFeaturesSnapshot snapshot(TradeFlowFeature tradeFlow) {
        return snapshot(tradeFlow, SignalRuleTestSupport.tradableQuality());
    }

    private static MarketFeaturesSnapshot snapshot(TradeFlowFeature tradeFlow, FeatureQuality quality) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(tradeFlow)
                .quality(quality)
                .build();
    }

    private static FeatureQuality warmingUpQuality() {
        return SignalRuleTestSupport.tradableQuality().toBuilder()
                .status(FeatureQualityStatus.DEGRADED)
                .warmingUp(true)
                .qualityReasons(List.of("WARMING_UP"))
                .build();
    }

    private static TradeFlowFeature tradeFlow(TradeFlowWindow w1s, TradeFlowWindow w5s,
                                              TradeFlowWindow w15s, TradeFlowWindow w60s) {
        return TradeFlowFeature.builder()
                .lastTradePrice(new BigDecimal("50003.0"))
                .window1s(w1s)
                .window5s(w5s)
                .window15s(w15s)
                .window60s(w60s)
                .build();
    }

    private static TradeFlowWindow populated(int tradeCount) {
        return TradeFlowWindow.builder()
                .buyAggressiveVolume(new BigDecimal("4.0"))
                .sellAggressiveVolume(new BigDecimal("2.0"))
                .totalAggressiveVolume(new BigDecimal("6.0"))
                .signedTradeFlow(new BigDecimal("2.0"))
                .signedFlowImbalance(new BigDecimal("0.3333"))
                .tradeCount(tradeCount)
                .validQtyTradeCount(tradeCount)
                .aggressiveTradeCount(tradeCount)
                .unknownSideCount(0)
                .tradeIntensity(new BigDecimal("10.0"))
                .avgTradeSize(new BigDecimal("0.12"))
                .vwap(new BigDecimal("50002.5"))
                .build();
    }
}
