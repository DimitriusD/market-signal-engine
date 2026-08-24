package com.trading.marketsignalengine.application.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.InterpretationValidityPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationAssemblyPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationSnapshotAssembler;
import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessmentResolver;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityEligibilityPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityHorizonPolicy;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import com.trading.marketsignalengine.application.domain.validation.InvalidMarketFeaturesSnapshotException;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.service.ValidatedMarketInterpretationEvaluator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * V2 replay parity: the harness runs exactly the live validated evaluator with an explicit,
 * recorded assessment instant — same inputs + instant + policies ⇒ value-equal snapshots with the
 * same deterministic ids as the live path; validation is never bypassed; no wall clock, no Kafka.
 */
class InterpretationReplayHarnessTest {

    private static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    private static final Instant ASSESSED_AT = EVENT_TIME.plusMillis(100);

    private final ValidatedMarketInterpretationEvaluator evaluator = evaluator();
    private final InterpretationReplayHarness harness = new InterpretationReplayHarness(evaluator);

    @Test
    void replayMatchesTheLiveEvaluationExactly() {
        MarketFeaturesSnapshot snapshot = bullishSnapshot();

        MarketInterpretationSnapshot live = evaluator.evaluate(snapshot, ASSESSED_AT);
        List<MarketInterpretationSnapshot> replayed =
                harness.replay(List.of(snapshot), InterpretationReplayHarness.fixed(ASSESSED_AT));

        assertEquals(1, replayed.size());
        assertEquals(live, replayed.getFirst(), "live and replay must be value-equal");
        assertEquals(live.interpretationSnapshotId(), replayed.getFirst().interpretationSnapshotId());
        assertEquals(OpportunityStatus.CANDIDATE, replayed.getFirst().marketOpportunity().status());
    }

    @Test
    void replayPreservesInputOrderAndCount() {
        MarketFeaturesSnapshot first = bullishSnapshot();
        MarketFeaturesSnapshot second = bullishSnapshot().toBuilder().snapshotId("snap-2").build();

        List<MarketInterpretationSnapshot> replayed =
                harness.replay(List.of(first, second), InterpretationReplayHarness.fixed(ASSESSED_AT));

        assertEquals(2, replayed.size());
        assertEquals("snap-1", replayed.get(0).featureLineage().sourceFeatureEventId());
        assertEquals("snap-2", replayed.get(1).featureLineage().sourceFeatureEventId());
        assertTrue(!replayed.get(0).interpretationSnapshotId().equals(replayed.get(1).interpretationSnapshotId()),
                "a different source event id must produce a different interpretation id");
    }

    @Test
    void replayNeverBypassesValidation() {
        MarketFeaturesSnapshot invalid = bullishSnapshot().toBuilder().featureSetVersion("mfs-features-v99").build();

        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> harness.replay(List.of(invalid), InterpretationReplayHarness.fixed(ASSESSED_AT)));
        List<MarketFeaturesSnapshot> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> harness.replay(withNull, InterpretationReplayHarness.fixed(ASSESSED_AT)));
    }

    @Test
    void defaultResolverPinsAssessmentToRecordedComputeTime() {
        MarketFeaturesSnapshot snapshot = bullishSnapshot();

        List<MarketInterpretationSnapshot> replayed = harness.replay(List.of(snapshot));

        // computedAt = EVENT_TIME + 25 ms in the fixture — deterministic, no wall clock involved
        assertEquals(evaluator.evaluate(snapshot, snapshot.computedAt()), replayed.getFirst());
    }

    @Test
    void nullResolverResultIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> harness.replay(List.of(bullishSnapshot()), features -> null));
    }

    // ------------------------------------------------------------------ fixtures

    private static ValidatedMarketInterpretationEvaluator evaluator() {
        HorizonInterpretationPolicy horizonPolicy = new HorizonInterpretationPolicy("horizon-fixture-v1",
                FlowAssessmentPolicy.of("horizon-flow-v1",
                        flowPolicy(MarketHorizon.H1S), flowPolicy(MarketHorizon.H5S),
                        flowPolicy(MarketHorizon.H15S), flowPolicy(MarketHorizon.H60S)),
                MomentumAssessmentPolicy.of("horizon-momentum-v1",
                        momentumPolicy(MarketHorizon.H5S), momentumPolicy(MarketHorizon.H15S),
                        momentumPolicy(MarketHorizon.H60S)),
                VolatilityAssessmentPolicy.of("horizon-volatility-v1",
                        volatilityPolicy(MarketHorizon.H1S), volatilityPolicy(MarketHorizon.H5S),
                        volatilityPolicy(MarketHorizon.H15S), volatilityPolicy(MarketHorizon.H60S)),
                new BookAssessmentPolicy("horizon-book-v1", 5,
                        bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50")));
        EnumMap<MarketHorizon, Duration> base = new EnumMap<>(Map.of(
                MarketHorizon.H1S, Duration.ofMillis(400), MarketHorizon.H5S, Duration.ofMillis(500),
                MarketHorizon.H15S, Duration.ofMillis(1_500), MarketHorizon.H60S, Duration.ofMillis(5_000)));
        return new ValidatedMarketInterpretationEvaluator(
                new MarketFeaturesSnapshotValidator(Set.of("mfs-features-v2")),
                new QualityAssessmentResolver(),
                new MarketInterpretationSnapshotAssembler(),
                QualityEligibilityPolicy.of(Duration.ofMillis(2_000), Duration.ofMillis(1_000), true),
                new MarketInterpretationAssemblyPolicy(
                        "mse-interpretation-fixture-v1", "cfg-interpretation-fixture-1",
                        new OpportunityInterpretationPolicy("opportunity-fixture-v1",
                                new CrossHorizonInterpretationPolicy("cross-fixture-v1", horizonPolicy), false),
                        new InterpretationValidityPolicy("validity-fixture-v1", base,
                                Duration.ofMillis(300), Duration.ofMillis(250),
                                Duration.ofMillis(100), Duration.ofMillis(50), Duration.ofMillis(25))));
    }

    private static MarketFeaturesSnapshot bullishSnapshot() {
        TradeFlowWindow w = TradeFlowWindow.builder()
                .signedFlowImbalance(bd("0.60"))
                .tradeCount(200).validQtyTradeCount(200).aggressiveTradeCount(150).unknownSideCount(0)
                .tradeIntensity(bd("3.0"))
                .build();
        BigDecimal move = bd("6");
        BigDecimal vol = bd("5");
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(TradeFlowFeature.builder().window1s(w).window5s(w).window15s(w).window60s(w).build())
                .regime(RegimeFeature.builder()
                        .realizedVolatilityBps1s(vol).realizedVolatilityBps5s(vol)
                        .realizedVolatilityBps15s(vol).realizedVolatilityBps60s(vol)
                        .priceChangeBps5s(move).priceChangeBps15s(move).priceChangeBps60s(move)
                        .build())
                .build();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static FlowHorizonPolicy flowPolicy(MarketHorizon horizon) {
        return FlowHorizonPolicy.of(horizon, bd("0.30"), bd("-0.30"), 10, 5, bd("0.5"));
    }

    private static MomentumHorizonPolicy momentumPolicy(MarketHorizon horizon) {
        return MomentumHorizonPolicy.of(horizon, bd("2"), bd("-2"), bd("10"), bd("50"));
    }

    private static VolatilityHorizonPolicy volatilityPolicy(MarketHorizon horizon) {
        return VolatilityHorizonPolicy.of(horizon, bd("2"), bd("8"), bd("15"));
    }
}
