package com.trading.marketsignalengine.application.service;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;

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
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fixtures of the V2 runtime tests (validated evaluator, live handler, replay harness): one explicit
 * aggregate policy chain over the same contract-valid MFS v2 snapshots as the Stage 7–9 domain
 * tests, so the runtime layer is exercised through the real Stage 3–9 pipeline. Deadlines with
 * {@code EVENT_TIME = T}: candidate H5S = T+400 ms; NO_OPPORTUNITY = T+200 ms; BLOCKED = T+150 ms.
 */
final class RuntimeFixtures {

    static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    /** Fresh assessment instant used by handler tests as the fixed clock instant. */
    static final Instant ASSESSED_AT = EVENT_TIME.plusMillis(100);

    static final QualityEligibilityPolicy QUALITY_POLICY =
            QualityEligibilityPolicy.of(Duration.ofMillis(2_000), Duration.ofMillis(1_000), true);

    static final HorizonInterpretationPolicy HORIZON_POLICY = new HorizonInterpretationPolicy("horizon-fixture-v1",
            FlowAssessmentPolicy.of("horizon-flow-v1",
                    flowPolicy(H1S), flowPolicy(H5S), flowPolicy(H15S), flowPolicy(H60S)),
            MomentumAssessmentPolicy.of("horizon-momentum-v1",
                    momentumPolicy(H5S), momentumPolicy(H15S), momentumPolicy(H60S)),
            VolatilityAssessmentPolicy.of("horizon-volatility-v1",
                    volatilityPolicy(H1S), volatilityPolicy(H5S), volatilityPolicy(H15S), volatilityPolicy(H60S)),
            new BookAssessmentPolicy("horizon-book-v1", 5,
                    bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50")));

    static final MarketInterpretationAssemblyPolicy ASSEMBLY_POLICY = new MarketInterpretationAssemblyPolicy(
            "mse-interpretation-fixture-v1",
            "cfg-interpretation-fixture-1",
            new OpportunityInterpretationPolicy("opportunity-fixture-v1",
                    new CrossHorizonInterpretationPolicy("cross-fixture-v1", HORIZON_POLICY), false),
            new InterpretationValidityPolicy("validity-fixture-v1", baseValidities(),
                    Duration.ofMillis(300), Duration.ofMillis(250),
                    Duration.ofMillis(100), Duration.ofMillis(50), Duration.ofMillis(25)));

    private RuntimeFixtures() {
    }

    /** The full live/replay evaluator over the fixture policies and the default MFS v2 allowlist. */
    static ValidatedMarketInterpretationEvaluator evaluator() {
        return new ValidatedMarketInterpretationEvaluator(
                new MarketFeaturesSnapshotValidator(Set.of("mfs-features-v2")),
                new QualityAssessmentResolver(),
                new MarketInterpretationSnapshotAssembler(),
                QUALITY_POLICY,
                ASSEMBLY_POLICY);
    }

    private static Map<MarketHorizon, Duration> baseValidities() {
        EnumMap<MarketHorizon, Duration> base = new EnumMap<>(MarketHorizon.class);
        base.put(H1S, Duration.ofMillis(400));
        base.put(H5S, Duration.ofMillis(500));
        base.put(H15S, Duration.ofMillis(1_500));
        base.put(H60S, Duration.ofMillis(5_000));
        return base;
    }

    static BigDecimal bd(String value) {
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

    // ------------------------------------------------------------------ snapshots

    static TradeFlowWindow window(String imbalance) {
        return TradeFlowWindow.builder()
                .signedFlowImbalance(imbalance == null ? null : bd(imbalance))
                .tradeCount(200)
                .validQtyTradeCount(200)
                .aggressiveTradeCount(150)
                .unknownSideCount(0)
                .tradeIntensity(bd("3.0"))
                .build();
    }

    static TradeFlowFeature uniformTradeFlow(String imbalance) {
        TradeFlowWindow w = window(imbalance);
        return TradeFlowFeature.builder().window1s(w).window5s(w).window15s(w).window60s(w).build();
    }

    /** Fully bullish continuation snapshot: an active LONG candidate under the fixture policies. */
    static MarketFeaturesSnapshot bullishSnapshot() {
        BigDecimal move = bd("6");
        BigDecimal vol = bd("5");
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniformTradeFlow("0.60"))
                .regime(RegimeFeature.builder()
                        .realizedVolatilityBps1s(vol).realizedVolatilityBps5s(vol)
                        .realizedVolatilityBps15s(vol).realizedVolatilityBps60s(vol)
                        .priceChangeBps5s(move).priceChangeBps15s(move).priceChangeBps60s(move)
                        .build())
                .bbo(BboFeature.builder()
                        .bestBidPrice(bd("100.00")).bestAskPrice(bd("100.02"))
                        .bestBidQty(bd("5")).bestAskQty(bd("5"))
                        .spreadAbs(bd("0.02")).spreadBps(bd("2"))
                        .midPrice(bd("100.01")).micropriceTop1(bd("100.011"))
                        .micropriceOffsetBps(bd("6"))
                        .build())
                .book(BookFeature.builder().levelsUsed(5).top5Imbalance(bd("0.60")).build())
                .build();
    }

    /** UNSAFE source quality — quality not eligible, opportunity must be BLOCKED. */
    static MarketFeaturesSnapshot unsafeSnapshot() {
        return bullishSnapshot().toBuilder()
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.UNSAFE)
                        .sourceOrderBookTrusted(false)
                        .sourceOrderBookReason("BOOK_UNTRUSTED")
                        .qualityReasons(List.of("BOOK_UNTRUSTED"))
                        .build())
                .build();
    }

    /** An unsupported feature-set version — rejected by the validator before any interpretation. */
    static MarketFeaturesSnapshot invalidSnapshot() {
        return bullishSnapshot().toBuilder().featureSetVersion("mfs-features-v99").build();
    }
}
