package com.trading.marketsignalengine.application.domain.interpretation.cross;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessmentResolver;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityEligibilityPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityHorizonPolicy;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Fixtures of the Stage 7 cross-horizon tests. Two layers:
 * <ul>
 *   <li><b>Interpreter layer</b> — hand-built {@link HorizonAssessments} with one line per horizon
 *       state (bullish/bearish/neutral/mixed/unknown-direction/unavailable), because the interpreter
 *       reads only typed fields. Fixture regimes are deliberately distinct so regime provenance is
 *       observable: directional = TRENDING, neutral = RANGING, mixed = VOLATILE.</li>
 *   <li><b>Boundary layer</b> — the same aggregate fixture policy and contract-valid MFS v2
 *       snapshots as the Stage 6 tests ({@code HorizonFixtures}), with the real Stage 3 resolver, so
 *       the safe public boundary is exercised end to end in the domain.</li>
 * </ul>
 */
final class CrossFixtures {

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

    static final CrossHorizonInterpretationPolicy POLICY =
            new CrossHorizonInterpretationPolicy("cross-fixture-v1", HORIZON_POLICY);

    private CrossFixtures() {
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

    // ------------------------------------------------------------------ hand-built horizon assessments

    static HorizonAssessment eligible(MarketHorizon horizon, InterpretationDirection direction,
                                      String strength, MarketRegime regime) {
        return HorizonAssessment.eligible(horizon, direction,
                strength == null ? null : EvidenceStrength.of(strength), regime, List.of(), List.of());
    }

    static HorizonAssessment bullish(MarketHorizon horizon, String strength) {
        return eligible(horizon, InterpretationDirection.BULLISH, strength, MarketRegime.TRENDING);
    }

    static HorizonAssessment bearish(MarketHorizon horizon, String strength) {
        return eligible(horizon, InterpretationDirection.BEARISH, strength, MarketRegime.TRENDING);
    }

    static HorizonAssessment neutral(MarketHorizon horizon) {
        return eligible(horizon, InterpretationDirection.NEUTRAL, "0", MarketRegime.RANGING);
    }

    static HorizonAssessment mixed(MarketHorizon horizon) {
        return eligible(horizon, InterpretationDirection.MIXED, null, MarketRegime.VOLATILE);
    }

    /** Eligible but not interpretable: direction UNKNOWN — never a participant. */
    static HorizonAssessment unknownDirection(MarketHorizon horizon) {
        return eligible(horizon, InterpretationDirection.UNKNOWN, null, MarketRegime.UNKNOWN);
    }

    static HorizonAssessment unavailable(MarketHorizon horizon) {
        return HorizonAssessment.unavailable(horizon, List.of());
    }

    /** Assessments in argument order H1S, H5S, H15S, H60S (canonical container order). */
    static HorizonAssessments assessments(HorizonAssessment h1s, HorizonAssessment h5s,
                                          HorizonAssessment h15s, HorizonAssessment h60s) {
        return HorizonAssessments.of(h1s, h5s, h15s, h60s);
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

    static RegimeFeature regime(String priceChangeBps, String volatilityBps) {
        BigDecimal vol = volatilityBps == null ? null : bd(volatilityBps);
        BigDecimal move = priceChangeBps == null ? null : bd(priceChangeBps);
        return RegimeFeature.builder()
                .realizedVolatilityBps1s(vol).realizedVolatilityBps5s(vol)
                .realizedVolatilityBps15s(vol).realizedVolatilityBps60s(vol)
                .priceChangeBps5s(move).priceChangeBps15s(move).priceChangeBps60s(move)
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
                .regime(regime(priceChangeBps, volatilityBps))
                .bbo(bbo(micropriceOffsetBps))
                .book(book(top5Imbalance))
                .build();
    }

    /** The real Stage 3 assessment of {@code snapshot} at a fresh {@link #ASSESSED_AT}. */
    static QualityAssessment quality(MarketFeaturesSnapshot snapshot) {
        return QUALITY_RESOLVER.resolve(snapshot, ASSESSED_AT, QUALITY_POLICY);
    }
}
