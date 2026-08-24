package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQuality;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossEvaluationTestFactory;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.quality.HorizonEligibilities;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixtures of the Stage 8 opportunity tests. Two layers:
 * <ul>
 *   <li><b>Resolver layer</b> — hand-built {@link HorizonAssessments} with explicit nested evidence
 *       (the resolver reads H15S MOMENTUM, H5S FLOW and per-horizon BOOK evidence), reduced by the
 *       real Stage 7 interpreter via {@link CrossEvaluationTestFactory}, plus resolver-produced
 *       eligible / blocked {@link QualityAssessment}s.</li>
 *   <li><b>Boundary layer</b> — the same aggregate fixture policy and contract-valid MFS v2
 *       snapshots as the Stage 7 tests, with the real Stage 3 resolver, so the safe public boundary
 *       is exercised end to end in the domain.</li>
 * </ul>
 */
final class OpportunityFixtures {

    static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    static final Instant ASSESSED_AT = EVENT_TIME.plusMillis(100);

    static final QualityEligibilityPolicy QUALITY_POLICY =
            QualityEligibilityPolicy.of(Duration.ofMillis(2_000), Duration.ofMillis(1_000), true);
    static final QualityAssessmentResolver QUALITY_RESOLVER = new QualityAssessmentResolver();

    static final HorizonInterpretationPolicy HORIZON_POLICY = new HorizonInterpretationPolicy("horizon-fixture-v1",
            FlowAssessmentPolicy.of("horizon-flow-v1",
                    flowPolicy(H1S), flowPolicy(H5S), flowPolicy(H15S), flowPolicy(H60S)),
            MomentumAssessmentPolicy.of("horizon-momentum-v1",
                    momentumPolicy(H5S), momentumPolicy(H15S), momentumPolicy(H60S)),
            VolatilityAssessmentPolicy.of("horizon-volatility-v1",
                    volatilityPolicy(H1S), volatilityPolicy(H5S), volatilityPolicy(H15S), volatilityPolicy(H60S)),
            new BookAssessmentPolicy("horizon-book-v1", 5,
                    bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50")));

    static final CrossHorizonInterpretationPolicy CROSS_POLICY =
            new CrossHorizonInterpretationPolicy("cross-fixture-v1", HORIZON_POLICY);

    /** The baseline Stage 8 policy: VOLATILE momentum continuation not allowed. */
    static final OpportunityInterpretationPolicy POLICY =
            new OpportunityInterpretationPolicy("opportunity-fixture-v1", CROSS_POLICY, false);
    /** Same policy with VOLATILE momentum continuation explicitly allowed. */
    static final OpportunityInterpretationPolicy ALLOW_VOLATILE_POLICY =
            new OpportunityInterpretationPolicy("opportunity-fixture-volatile-v1", CROSS_POLICY, true);

    private OpportunityFixtures() {
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

    // ------------------------------------------------------------------ quality assessments

    /** The real Stage 3 assessment of {@code snapshot} at a fresh {@link #ASSESSED_AT}. */
    static QualityAssessment quality(MarketFeaturesSnapshot snapshot) {
        return QUALITY_RESOLVER.resolve(snapshot, ASSESSED_AT, QUALITY_POLICY);
    }

    /** OK quality, every horizon ELIGIBLE, eligibleForTrading = true. */
    static QualityAssessment eligibleQuality() {
        return quality(snapshot("0.60", "6", "5", "0.60", "6"));
    }

    /** DEGRADED (trade-history gap: 1S/5S only) but still eligibleForTrading = true. */
    static QualityAssessment degradedEligibleQuality() {
        return quality(historyGapSnapshot());
    }

    /** UNSAFE source (untrusted book) — eligibleForTrading = false. */
    static QualityAssessment unsafeBlockedQuality() {
        return quality(unsafeSnapshot());
    }

    /** NO_DATA source — eligibleForTrading = false. */
    static QualityAssessment noDataBlockedQuality() {
        return quality(SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniformTradeFlow("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.NO_DATA)
                        .staleTrades(true)
                        .qualityReasons(List.of("NO_MARKET_DATA"))
                        .build())
                .build());
    }

    /** Hand-built UNKNOWN interpretation quality — eligibleForTrading = false by the enforced table. */
    static QualityAssessment unknownQuality() {
        QualityAssessment base = eligibleQuality();
        return new QualityAssessment(base.sourceQualityStatus(),
                InterpretationQuality.unknown(List.of(ReasonCode.of("QUALITY_STATUS_UNKNOWN"))),
                base.timing(),
                HorizonEligibilities.uniform(HorizonEligibility.unknown(List.of())),
                List.of(), false);
    }

    // ------------------------------------------------------------------ hand-built evidence

    static EvidenceAssessment evidence(EvidenceDimension dimension, InterpretationDirection direction,
                                       String strength) {
        return EvidenceAssessment.available(dimension, direction,
                strength == null ? null : EvidenceStrength.of(strength), List.of());
    }

    static EvidenceAssessment notAvailable(EvidenceDimension dimension, EvidenceAvailabilityStatus status) {
        return EvidenceAssessment.notAvailable(dimension, status, List.of());
    }

    // ------------------------------------------------------------------ hand-built horizon assessments

    /** An ELIGIBLE horizon; {@code null} evidence entries are dropped. */
    static HorizonAssessment horizon(MarketHorizon horizon, InterpretationDirection direction, String strength,
                                     MarketRegime regime, EvidenceAssessment... evidence) {
        List<EvidenceAssessment> present = new ArrayList<>();
        for (EvidenceAssessment assessment : evidence) {
            if (assessment != null) {
                present.add(assessment);
            }
        }
        return HorizonAssessment.eligible(horizon, direction,
                strength == null ? null : EvidenceStrength.of(strength), regime, present, List.of());
    }

    static HorizonAssessment unavailable(MarketHorizon horizon) {
        return HorizonAssessment.unavailable(horizon, List.of());
    }

    /**
     * A fully aligned set on {@code direction}: every horizon directional with strength 0.6, TRENDING
     * regime, H15S MOMENTUM / H5S FLOW / H1S BOOK evidence confirming.
     */
    static HorizonAssessments alignedAssessments(InterpretationDirection direction) {
        return alignedAssessments(direction, "0.6", MarketRegime.TRENDING,
                evidence(EvidenceDimension.MOMENTUM, direction, "0.6"),
                evidence(EvidenceDimension.FLOW, direction, "0.6"),
                evidence(EvidenceDimension.BOOK, direction, "0.6"));
    }

    /**
     * A fully aligned set on {@code direction}, with the structural strength, the (dominant H60S)
     * regime and the gate-relevant evidence readings injectable: {@code h15Momentum} replaces the
     * H15S MOMENTUM evidence, {@code h5Flow} the H5S FLOW evidence, {@code h1Book} the H1S BOOK
     * evidence ({@code null} = that evidence absent). All four horizon directions stay on
     * {@code direction}, so the Stage 7 interpreter still reduces to ALIGNED_*.
     */
    static HorizonAssessments alignedAssessments(InterpretationDirection direction, String structuralStrength,
                                                 MarketRegime regime,
                                                 EvidenceAssessment h15Momentum,
                                                 EvidenceAssessment h5Flow,
                                                 EvidenceAssessment h1Book) {
        HorizonAssessment h60 = horizon(H60S, direction, structuralStrength, regime,
                evidence(EvidenceDimension.FLOW, direction, "0.6"),
                evidence(EvidenceDimension.MOMENTUM, direction, "0.6"));
        HorizonAssessment h15 = horizon(H15S, direction, structuralStrength, regime,
                evidence(EvidenceDimension.FLOW, direction, "0.6"), h15Momentum);
        HorizonAssessment h5 = horizon(H5S, direction, structuralStrength, regime,
                h5Flow, evidence(EvidenceDimension.MOMENTUM, direction, "0.6"));
        HorizonAssessment h1 = horizon(H1S, direction, "0.6", regime,
                evidence(EvidenceDimension.FLOW, direction, "0.6"), h1Book);
        return HorizonAssessments.of(h1, h5, h15, h60);
    }

    // ------------------------------------------------------------------ cross evaluations

    static CrossHorizonEvaluation evaluation(HorizonAssessments assessments) {
        return CrossEvaluationTestFactory.interpret(assessments);
    }

    static CrossHorizonEvaluation pair(HorizonAssessments assessments, CrossHorizonAssessment cross) {
        return CrossEvaluationTestFactory.pair(assessments, cross);
    }

    /** ALIGNED_* on {@code direction} with every candidate gate passing. */
    static CrossHorizonEvaluation alignedEvaluation(InterpretationDirection direction) {
        return evaluation(alignedAssessments(direction));
    }

    /** PARTIALLY_ALIGNED: neutral H5S under a bullish H60S/H15S structure. */
    static CrossHorizonEvaluation partiallyAlignedEvaluation() {
        return evaluation(HorizonAssessments.of(
                horizon(H1S, InterpretationDirection.BULLISH, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.FLOW, InterpretationDirection.BULLISH, "0.6")),
                horizon(H5S, InterpretationDirection.NEUTRAL, "0", MarketRegime.RANGING,
                        evidence(EvidenceDimension.FLOW, InterpretationDirection.NEUTRAL, "0")),
                horizon(H15S, InterpretationDirection.BULLISH, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, InterpretationDirection.BULLISH, "0.6")),
                horizon(H60S, InterpretationDirection.BULLISH, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, InterpretationDirection.BULLISH, "0.6"))));
    }

    /** CONFLICTING: bearish H5S trigger against a bullish H60S/H15S structure. */
    static CrossHorizonEvaluation conflictingEvaluation() {
        return evaluation(HorizonAssessments.of(
                horizon(H1S, InterpretationDirection.BULLISH, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.FLOW, InterpretationDirection.BULLISH, "0.6")),
                horizon(H5S, InterpretationDirection.BEARISH, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.FLOW, InterpretationDirection.BEARISH, "0.6")),
                horizon(H15S, InterpretationDirection.BULLISH, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, InterpretationDirection.BULLISH, "0.6")),
                horizon(H60S, InterpretationDirection.BULLISH, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.MOMENTUM, InterpretationDirection.BULLISH, "0.6"))));
    }

    /** NEUTRAL: every horizon a real interpreted neutral. */
    static CrossHorizonEvaluation neutralEvaluation() {
        return evaluation(HorizonAssessments.of(
                horizon(H1S, InterpretationDirection.NEUTRAL, "0", MarketRegime.RANGING,
                        evidence(EvidenceDimension.BOOK, InterpretationDirection.BULLISH, "0.6")),
                horizon(H5S, InterpretationDirection.NEUTRAL, "0", MarketRegime.RANGING),
                horizon(H15S, InterpretationDirection.NEUTRAL, "0", MarketRegime.RANGING),
                horizon(H60S, InterpretationDirection.NEUTRAL, "0", MarketRegime.RANGING)));
    }

    /** INSUFFICIENT_DATA: a lone bullish H5S trigger without any senior horizon. */
    static CrossHorizonEvaluation insufficientEvaluation() {
        return evaluation(HorizonAssessments.of(
                unavailable(H1S),
                horizon(H5S, InterpretationDirection.BULLISH, "0.6", MarketRegime.TRENDING,
                        evidence(EvidenceDimension.FLOW, InterpretationDirection.BULLISH, "0.6")),
                unavailable(H15S),
                unavailable(H60S)));
    }

    /** UNKNOWN cross verdict as a valid domain fixture (the interpreter never produces it). */
    static CrossHorizonEvaluation unknownEvaluation() {
        return pair(HorizonAssessments.of(unavailable(H1S), unavailable(H5S), unavailable(H15S), unavailable(H60S)),
                CrossHorizonAssessment.unknown(List.of()));
    }

    // ------------------------------------------------------------------ boundary snapshots

    /** A fully computed, active trade-flow window with the given signed imbalance. */
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

    static BboFeature bbo(String micropriceOffsetBps) {
        if (micropriceOffsetBps == null) {
            return null;
        }
        return BboFeature.builder()
                .bestBidPrice(bd("100.00")).bestAskPrice(bd("100.02"))
                .bestBidQty(bd("5")).bestAskQty(bd("5"))
                .spreadAbs(bd("0.02")).spreadBps(bd("2"))
                .midPrice(bd("100.01")).micropriceTop1(bd("100.011"))
                .micropriceOffsetBps(bd(micropriceOffsetBps))
                .build();
    }

    static BookFeature book(String top5Imbalance) {
        if (top5Imbalance == null) {
            return null;
        }
        return BookFeature.builder().levelsUsed(5).top5Imbalance(bd(top5Imbalance)).build();
    }

    /** Uniform realized volatility and per-horizon price changes ({@code null} = not computed). */
    static RegimeFeature regime(String priceChangeBps5s, String priceChangeBps15s, String priceChangeBps60s,
                                String volatilityBps) {
        BigDecimal vol = volatilityBps == null ? null : bd(volatilityBps);
        return RegimeFeature.builder()
                .realizedVolatilityBps1s(vol).realizedVolatilityBps5s(vol)
                .realizedVolatilityBps15s(vol).realizedVolatilityBps60s(vol)
                .priceChangeBps5s(priceChangeBps5s == null ? null : bd(priceChangeBps5s))
                .priceChangeBps15s(priceChangeBps15s == null ? null : bd(priceChangeBps15s))
                .priceChangeBps60s(priceChangeBps60s == null ? null : bd(priceChangeBps60s))
                .build();
    }

    /**
     * A fully eligible snapshot with one value per evidence dimension: the same flow imbalance /
     * price change / realized volatility on every horizon, and the given 1S book indicators.
     */
    static MarketFeaturesSnapshot snapshot(String imbalance, String priceChangeBps, String volatilityBps,
                                           String top5Imbalance, String micropriceOffsetBps) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniformTradeFlow(imbalance))
                .regime(regime(priceChangeBps, priceChangeBps, priceChangeBps, volatilityBps))
                .bbo(bbo(micropriceOffsetBps))
                .book(book(top5Imbalance))
                .build();
    }

    /** 1S/5S computed only, DEGRADED with a trade-history gap; still eligible for trading. */
    static MarketFeaturesSnapshot historyGapSnapshot() {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(TradeFlowFeature.builder().window1s(window("0.60")).window5s(window("0.60")).build())
                .regime(regime("6", "6", "6", "5"))
                .bbo(bbo("6"))
                .book(book("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("TRADE_HISTORY_GAP"))
                        .build())
                .build();
    }

    /** UNSAFE source quality (untrusted order book) — blocked for trading. */
    static MarketFeaturesSnapshot unsafeSnapshot() {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniformTradeFlow("0.60"))
                .regime(regime("6", "6", "6", "5"))
                .bbo(bbo("6"))
                .book(book("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.UNSAFE)
                        .sourceOrderBookTrusted(false)
                        .sourceOrderBookReason("BOOK_UNTRUSTED")
                        .qualityReasons(List.of("BOOK_UNTRUSTED"))
                        .build())
                .build();
    }
}
