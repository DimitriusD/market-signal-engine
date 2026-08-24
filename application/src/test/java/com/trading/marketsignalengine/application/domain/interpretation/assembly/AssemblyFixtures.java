package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;

import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.MarketOpportunityEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.MarketOpportunityEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityInterpretationPolicy;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fixtures of the Stage 9 assembly tests: the Stage 8 aggregate policy chain, an explicit
 * millisecond validity policy with easily assertable deadlines, and the same contract-valid MFS v2
 * snapshots as the Stage 7/8 tests, so assembly is exercised end to end through the real Stage 3–8
 * evaluators.
 *
 * <p>Deadlines with {@code EVENT_TIME = T}: candidate H5S OK = T+400 ms; candidate H5S DEGRADED =
 * T+350 ms; candidate H5S VOLATILE = T+375 ms; NO_OPPORTUNITY = T+200 ms; BLOCKED = T+150 ms.
 */
final class AssemblyFixtures {

    static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    /** Default fresh assessment instant: 100 ms after the source evaluation tick. */
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

    static final OpportunityInterpretationPolicy OPPORTUNITY_POLICY =
            new OpportunityInterpretationPolicy("opportunity-fixture-v1", CROSS_POLICY, false);
    static final OpportunityInterpretationPolicy ALLOW_VOLATILE_OPPORTUNITY_POLICY =
            new OpportunityInterpretationPolicy("opportunity-fixture-volatile-v1", CROSS_POLICY, true);

    static final InterpretationValidityPolicy VALIDITY_POLICY = new InterpretationValidityPolicy(
            "validity-fixture-v1",
            baseValidities(),
            Duration.ofMillis(300),
            Duration.ofMillis(250),
            Duration.ofMillis(100),
            Duration.ofMillis(50),
            Duration.ofMillis(25));

    static final MarketInterpretationAssemblyPolicy POLICY = new MarketInterpretationAssemblyPolicy(
            "mse-interpretation-fixture-v1", "cfg-interpretation-fixture-1", OPPORTUNITY_POLICY, VALIDITY_POLICY);
    static final MarketInterpretationAssemblyPolicy ALLOW_VOLATILE_POLICY = new MarketInterpretationAssemblyPolicy(
            "mse-interpretation-fixture-v1", "cfg-interpretation-fixture-1",
            ALLOW_VOLATILE_OPPORTUNITY_POLICY, VALIDITY_POLICY);

    static final MarketOpportunityEvaluator OPPORTUNITY_EVALUATOR = new MarketOpportunityEvaluator();

    private AssemblyFixtures() {
    }

    /** H1S 400 ms, H5S 500 ms, H15S 1500 ms, H60S 5000 ms — a fresh mutable copy per call. */
    static Map<MarketHorizon, Duration> baseValidities() {
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

    // ------------------------------------------------------------------ quality

    static QualityAssessment quality(MarketFeaturesSnapshot snapshot) {
        return quality(snapshot, ASSESSED_AT);
    }

    static QualityAssessment quality(MarketFeaturesSnapshot snapshot, Instant assessedAt) {
        return QUALITY_RESOLVER.resolve(snapshot, assessedAt, QUALITY_POLICY);
    }

    /** The Stage 8 evaluation the assembler must agree with, for the default fresh assessment. */
    static MarketOpportunityEvaluation opportunityEvaluation(MarketFeaturesSnapshot snapshot,
                                                             OpportunityInterpretationPolicy policy) {
        return OPPORTUNITY_EVALUATOR.evaluate(snapshot, quality(snapshot), policy);
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

    static MarketFeaturesSnapshot snapshot(String imbalance, String priceChangeBps, String volatilityBps,
                                           String top5Imbalance, String micropriceOffsetBps) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniformTradeFlow(imbalance))
                .regime(regime(priceChangeBps, priceChangeBps, priceChangeBps, volatilityBps))
                .bbo(bbo(micropriceOffsetBps))
                .book(book(top5Imbalance))
                .build();
    }

    /** Fully bullish continuation snapshot: candidate LONG under the fixture policies. */
    static MarketFeaturesSnapshot bullishSnapshot() {
        return snapshot("0.60", "6", "5", "0.60", "6");
    }

    static MarketFeaturesSnapshot bearishSnapshot() {
        return snapshot("-0.60", "-6", "5", "-0.60", "-6");
    }

    /** Realized volatility 12 bps (HIGH): VOLATILE regime everywhere, alignment fully bullish. */
    static MarketFeaturesSnapshot volatileSnapshot() {
        return snapshot("0.60", "6", "12", "0.60", "6");
    }

    /** Bearish 1S flow under a bullish structure: PARTIALLY_ALIGNED. */
    static MarketFeaturesSnapshot partialSnapshot() {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(TradeFlowFeature.builder()
                        .window1s(window("-0.60")).window5s(window("0.60"))
                        .window15s(window("0.60")).window60s(window("0.60"))
                        .build())
                .regime(regime("6", "6", "6", "5"))
                .build();
    }

    /** 60S price falls while 5S/15S rise: structural CONFLICTING. */
    static MarketFeaturesSnapshot conflictSnapshot() {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniformTradeFlow("0.60"))
                .regime(regime("6", "6", "-6", "5"))
                .bbo(bbo("6"))
                .book(book("0.60"))
                .build();
    }

    /** Bullish flow/momentum, bearish 1S order book: Book contradiction. */
    static MarketFeaturesSnapshot adverseBookSnapshot() {
        return snapshot("0.60", "6", "5", "-0.60", "-6");
    }

    /** 1S/5S computed only, DEGRADED with a trade-history gap: INSUFFICIENT_DATA cross verdict. */
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

    /** Fully bullish market whose source quality is DEGRADED (incomplete book) — still eligible. */
    static MarketFeaturesSnapshot degradedEligibleSnapshot() {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniformTradeFlow("0.60"))
                .regime(regime("6", "6", "6", "5"))
                .bbo(bbo("6"))
                .book(book("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .incompleteBook(true)
                        .qualityReasons(List.of("INCOMPLETE_BOOK"))
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

    /** NO_DATA source quality. */
    static MarketFeaturesSnapshot noDataSnapshot() {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(uniformTradeFlow("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.NO_DATA)
                        .staleTrades(true)
                        .qualityReasons(List.of("NO_MARKET_DATA"))
                        .build())
                .build();
    }
}
