package com.trading.marketsignalengine.application.domain.interpretation.quality;

import static com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus.ELIGIBLE;
import static com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus.FAILED;
import static com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus.UNAVAILABLE;
import static com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus.UNTRUSTED;
import static com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus.WARMING_UP;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.allComputed;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.allWindows;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.populated;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.shortWindowsOnly;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.snapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.quality.QualityFixtures.tradeFlow;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.availability.FeatureAvailabilityResolver;
import com.trading.marketsignalengine.application.domain.availability.FeatureAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.availability.FeatureWindowAvailability;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Per-horizon eligibility from trade-flow availability: the mapping table, the special NO_DATA /
 * calculator-failure / history-gap / warm-up / stale-trades / absent-group rules, per-feature
 * degradation and the guarantee that null never becomes ELIGIBLE.
 */
class HorizonEligibilityResolverTest {

    private final HorizonEligibilityResolver resolver = new HorizonEligibilityResolver();

    @Test
    void allWindowsComputedMakesEveryHorizonEligibleWithoutReasons() {
        HorizonEligibilities eligibilities = resolver.resolve(allComputed());

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(ELIGIBLE, eligibilities.statusOf(horizon), horizon.wireValue());
            assertTrue(eligibilities.of(horizon).reasonCodes().isEmpty(), "ELIGIBLE carries no reasons");
        }
        assertTrue(eligibilities.allEligible());
        assertEquals(MarketHorizon.canonicalOrder(), eligibilities.eligibleHorizons());
    }

    @Test
    void shortHorizonsStayEligibleWhileLongHorizonsWarmUp() {
        HorizonEligibilities eligibilities = resolver.resolve(
                snapshot(shortWindowsOnly(), QualityFixtures.warmingUpQuality()));

        assertEquals(ELIGIBLE, eligibilities.statusOf(H1S));
        assertEquals(ELIGIBLE, eligibilities.statusOf(H5S));
        assertEquals(WARMING_UP, eligibilities.statusOf(H15S));
        assertEquals(WARMING_UP, eligibilities.statusOf(H60S));
        assertEquals(List.of(QualityReasonCodes.WINDOW_WARMING_UP), eligibilities.of(H60S).reasonCodes());
        assertEquals(List.of(H1S, H5S), eligibilities.eligibleHorizons());
        assertTrue(eligibilities.anyEligible());
        assertFalse(eligibilities.allEligible());
    }

    @Test
    void historyGapMakesOnlyUncoveredHorizonsUntrusted() {
        HorizonEligibilities eligibilities = resolver.resolve(
                snapshot(shortWindowsOnly(), QualityFixtures.historyGapQuality()));

        assertEquals(ELIGIBLE, eligibilities.statusOf(H1S));
        assertEquals(ELIGIBLE, eligibilities.statusOf(H5S));
        assertEquals(UNTRUSTED, eligibilities.statusOf(H15S));
        assertEquals(UNTRUSTED, eligibilities.statusOf(H60S));
        assertEquals(List.of(QualityReasonCodes.TRADE_HISTORY_GAP), eligibilities.of(H15S).reasonCodes());
    }

    @Test
    void staleTradesMakeEveryTradeBasedHorizonUntrusted() {
        HorizonEligibilities eligibilities = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.staleTradesQuality()));

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(UNTRUSTED, eligibilities.statusOf(horizon), horizon.wireValue());
            assertEquals(List.of(QualityReasonCodes.STALE_TRADES), eligibilities.of(horizon).reasonCodes());
        }
        assertFalse(eligibilities.anyEligible());
    }

    @Test
    void tradeFlowCalculatorFailureFailsEveryHorizon() {
        HorizonEligibilities eligibilities = resolver.resolve(
                snapshot(allWindows(), QualityFixtures.calculatorFailureQuality(), List.of("trade-flow")));

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(FAILED, eligibilities.statusOf(horizon), horizon.wireValue());
            assertEquals(List.of(QualityReasonCodes.TRADE_FLOW_CALCULATOR_FAILED),
                    eligibilities.of(horizon).reasonCodes());
        }
    }

    @Test
    void otherGroupFailuresDoNotFailTradeFlowHorizons() {
        HorizonEligibilities eligibilities = resolver.resolve(snapshot(
                allWindows(), QualityFixtures.calculatorFailureQuality(),
                List.of("bbo", "order-book", "short-term-regime")));

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(ELIGIBLE, eligibilities.statusOf(horizon), horizon.wireValue());
        }
    }

    @Test
    void absentTradeFlowGroupIsUnavailable() {
        HorizonEligibilities eligibilities = resolver.resolve(snapshot(null));

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(UNAVAILABLE, eligibilities.statusOf(horizon), horizon.wireValue());
            assertEquals(List.of(QualityReasonCodes.TRADE_FLOW_GROUP_ABSENT), eligibilities.of(horizon).reasonCodes());
        }
    }

    @Test
    void absentWindowInsidePresentGroupIsUnavailableNotComputed() {
        HorizonEligibilities eligibilities = resolver.resolve(snapshot(
                tradeFlow(populated(9), populated(50), TradeFlowWindow.builder().build(), null)));

        assertEquals(ELIGIBLE, eligibilities.statusOf(H1S));
        assertEquals(ELIGIBLE, eligibilities.statusOf(H5S));
        assertEquals(UNAVAILABLE, eligibilities.statusOf(H15S));
        assertEquals(UNAVAILABLE, eligibilities.statusOf(H60S));
        assertEquals(List.of(QualityReasonCodes.WINDOW_NOT_COMPUTED), eligibilities.of(H15S).reasonCodes());
        assertEquals(List.of(QualityReasonCodes.WINDOW_NOT_COMPUTED), eligibilities.of(H60S).reasonCodes());
    }

    @Test
    void noDataMakesEveryHorizonUnavailableNotUntrusted() {
        // The producer sets staleTrades=true on NO_DATA too; absent data is not "untrusted data".
        HorizonEligibilities eligibilities = resolver.resolve(snapshot(null, QualityFixtures.noDataQuality()));

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(UNAVAILABLE, eligibilities.statusOf(horizon), horizon.wireValue());
            assertEquals(List.of(QualityReasonCodes.SOURCE_NO_DATA), eligibilities.of(horizon).reasonCodes());
        }
    }

    @Test
    void nullOrWireDefaultWindowNeverBecomesEligible() {
        TradeFlowWindow wireDefault = TradeFlowWindow.builder()
                .tradeCount(0).validQtyTradeCount(0).aggressiveTradeCount(0).unknownSideCount(0)
                .build();
        HorizonEligibilities eligibilities = resolver.resolve(snapshot(
                tradeFlow(wireDefault, null, TradeFlowWindow.builder().build(), null)));

        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertNotEquals(ELIGIBLE, eligibilities.statusOf(horizon), horizon.wireValue());
            assertFalse(eligibilities.of(horizon).reasonCodes().isEmpty(), "non-eligible verdict explains itself");
        }
        assertFalse(eligibilities.anyEligible());
    }

    @Test
    void canonicalOrderIsAlways1s5s15s60s() {
        HorizonEligibilities eligibilities = resolver.resolve(
                snapshot(shortWindowsOnly(), QualityFixtures.warmingUpQuality()));

        assertEquals(List.of(H1S, H5S, H15S, H60S), new ArrayList<>(eligibilities.asMap().keySet()));
        assertEquals(
                List.of(ELIGIBLE, ELIGIBLE, WARMING_UP, WARMING_UP),
                eligibilities.asList().stream().map(HorizonEligibility::status).toList());
        assertEquals(4, eligibilities.asList().size());
    }

    @Test
    void availabilityToEligibilityMappingTable() {
        assertMapped(FeatureAvailabilityStatus.AVAILABLE, ELIGIBLE, FeatureAvailabilityResolver.CODE_WINDOW_COMPUTED);
        assertMapped(FeatureAvailabilityStatus.WARMING_UP, WARMING_UP, FeatureAvailabilityResolver.CODE_WINDOW_WARMING_UP);
        assertMapped(FeatureAvailabilityStatus.UNAVAILABLE, UNAVAILABLE, FeatureAvailabilityResolver.CODE_WINDOW_NOT_COMPUTED);
        assertMapped(FeatureAvailabilityStatus.UNTRUSTED, UNTRUSTED, FeatureAvailabilityResolver.CODE_STALE_TRADES);
        assertMapped(FeatureAvailabilityStatus.FAILED, FAILED, FeatureAvailabilityResolver.CODE_CALCULATOR_FAILED);
    }

    private static void assertMapped(FeatureAvailabilityStatus availability, HorizonEligibilityStatus expected, String code) {
        HorizonEligibility eligibility = HorizonEligibilityResolver.toEligibility(
                FeatureWindowAvailability.of(H5S, availability, code));
        assertEquals(expected, eligibility.status());
        if (expected == ELIGIBLE) {
            assertTrue(eligibility.reasonCodes().isEmpty(), "WINDOW_COMPUTED is not carried as a reason");
        } else {
            assertEquals(List.of(ReasonCode.of(code)), eligibility.reasonCodes());
        }
    }

    @Test
    void resultIsDeterministicForEqualInputs() {
        MarketFeaturesSnapshot snapshot = snapshot(shortWindowsOnly(), QualityFixtures.historyGapQuality());

        assertEquals(resolver.resolve(snapshot), new HorizonEligibilityResolver().resolve(snapshot));
    }

    @Test
    void failsFastOnMissingSnapshotOrQuality() {
        assertThrows(NullPointerException.class, () -> resolver.resolve(null));
        assertThrows(NullPointerException.class, () -> resolver.resolve(allComputed().toBuilder().quality(null).build()));
        assertThrows(NullPointerException.class, () -> resolver.resolve(
                snapshot(allWindows(), QualityFixtures.okQuality().toBuilder().status(null).build())));
    }
}
