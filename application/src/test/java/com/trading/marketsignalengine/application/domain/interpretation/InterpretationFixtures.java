package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Valid, fully explicit V2 fixtures. The id fixture is pinned in
 * {@link InterpretationSnapshotIdGeneratorTest}; every other test derives variations from these by
 * changing exactly one thing.
 */
final class InterpretationFixtures {

    static final Instant EVALUATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    static final Instant COMPUTED_AT = EVALUATED_AT.plusMillis(25);
    static final Instant VALID_UNTIL = EVALUATED_AT.plusMillis(5_000);

    static final ReasonCode WINDOW_COMPUTED = ReasonCode.of("WINDOW_COMPUTED");
    static final ReasonCode WINDOW_WARMING_UP = ReasonCode.of("WINDOW_WARMING_UP");
    static final ReasonCode STALE_TRADES = ReasonCode.of("STALE_TRADES");
    static final ReasonCode FLOW_BULLISH = ReasonCode.of("FLOW_IMBALANCE_BULLISH");
    static final ReasonCode ALL_ELIGIBLE_BULLISH = ReasonCode.of("ALL_ELIGIBLE_HORIZONS_BULLISH");
    static final ReasonCode TOO_FEW_ELIGIBLE = ReasonCode.of("TOO_FEW_ELIGIBLE_HORIZONS");
    static final ReasonCode QUALITY_BLOCKED = ReasonCode.of("QUALITY_BLOCKED");
    static final ReasonCode NEUTRAL_MARKET = ReasonCode.of("NEUTRAL_MARKET");
    static final ReasonCode ALIGNED_WITH_TRIGGER = ReasonCode.of("ALIGNED_BULLISH_WITH_TRIGGER");
    static final ReasonCode FLOW_FLIPPED = ReasonCode.of("FLOW_IMBALANCE_FLIPPED");

    private InterpretationFixtures() {
    }

    // ------------------------------------------------------------------ lineage

    static FeatureLineage featureLineage() {
        return new FeatureLineage("feat-0001", 1, "mfs-features-v2", "cfg-test-mfs-v2",
                EVALUATED_AT, COMPUTED_AT, "TRADE");
    }

    static InterpretationLineage interpretationLineage() {
        return new InterpretationLineage("mse-interpretation-v1", "cfg-test-interpretation-v1");
    }

    // ------------------------------------------------------------------ strength

    static EvidenceStrength strength(String value) {
        return EvidenceStrength.of(value);
    }

    // ------------------------------------------------------------------ horizons

    static EvidenceAssessment bullishFlow() {
        return EvidenceAssessment.available(EvidenceDimension.FLOW, InterpretationDirection.BULLISH,
                strength("0.6"), List.of(FLOW_BULLISH));
    }

    static HorizonAssessment eligibleBullish(MarketHorizon horizon) {
        return HorizonAssessment.eligible(horizon, InterpretationDirection.BULLISH, strength("0.6"),
                MarketRegime.TRENDING, List.of(bullishFlow()), List.of());
    }

    static HorizonAssessment eligibleNeutral(MarketHorizon horizon) {
        return HorizonAssessment.eligible(horizon, InterpretationDirection.NEUTRAL, strength("0.1"),
                null, List.of(), List.of());
    }

    static HorizonAssessment warmingUp(MarketHorizon horizon) {
        return HorizonAssessment.warmingUp(horizon, List.of(WINDOW_WARMING_UP));
    }

    /** All four horizons ELIGIBLE and bullish, in canonical order. */
    static List<HorizonAssessment> allEligibleBullish() {
        List<HorizonAssessment> list = new ArrayList<>();
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            list.add(eligibleBullish(horizon));
        }
        return list;
    }

    /** 1S / 5S eligible bullish, 15S / 60S warming up. */
    static List<HorizonAssessment> shortEligibleLongWarmingUp() {
        return List.of(eligibleBullish(H1S), eligibleBullish(H5S), warmingUp(H15S), warmingUp(H60S));
    }

    static List<HorizonAssessment> allWarmingUp() {
        List<HorizonAssessment> list = new ArrayList<>();
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            list.add(warmingUp(horizon));
        }
        return list;
    }

    // ------------------------------------------------------------------ cross-horizon

    static CrossHorizonAssessment alignedBullishAllHorizons() {
        return CrossHorizonAssessment.alignedBullish(strength("0.7"), H60S, MarketHorizon.canonicalOrder(),
                MarketRegime.TRENDING, List.of(ALL_ELIGIBLE_BULLISH));
    }

    static CrossHorizonAssessment insufficientData(List<MarketHorizon> participating) {
        return CrossHorizonAssessment.insufficientData(participating, List.of(TOO_FEW_ELIGIBLE));
    }

    // ------------------------------------------------------------------ opportunity

    static MarketOpportunity candidateLong5s() {
        return MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION, OpportunitySide.LONG, H5S,
                strength("0.65"), List.of(ALIGNED_WITH_TRIGGER), List.of(FLOW_FLIPPED));
    }

    // ------------------------------------------------------------------ aggregate

    /** A valid, fully eligible snapshot: quality OK, all horizons bullish, aligned, LONG candidate on 5S. */
    static MarketInterpretationSnapshot.Builder validSnapshotBuilder() {
        return MarketInterpretationSnapshot.builder()
                .exchange("binance")
                .marketType("spot")
                .base("BTC")
                .quote("USDT")
                .symbol("BTCUSDT")
                .instrumentId("binance:spot:BTCUSDT")
                .evaluatedAt(EVALUATED_AT)
                .validUntil(VALID_UNTIL)
                .interpretationQuality(InterpretationQuality.ok(List.of()))
                .horizonAssessments(allEligibleBullish())
                .crossHorizonAssessment(alignedBullishAllHorizons())
                .marketOpportunity(candidateLong5s())
                .featureLineage(featureLineage())
                .interpretationLineage(interpretationLineage());
    }

    /** A valid blocked snapshot: everything warming up, quality BLOCKED, opportunity BLOCKED. */
    static MarketInterpretationSnapshot.Builder blockedSnapshotBuilder() {
        return validSnapshotBuilder()
                .interpretationQuality(InterpretationQuality.blocked(List.of(QUALITY_BLOCKED)))
                .horizonAssessments(allWarmingUp())
                .crossHorizonAssessment(insufficientData(List.of()))
                .marketOpportunity(MarketOpportunity.blocked(List.of(QUALITY_BLOCKED)));
    }
}
