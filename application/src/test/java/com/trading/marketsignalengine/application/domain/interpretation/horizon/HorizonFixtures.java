package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;

import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowHorizonPolicy;
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

/**
 * Fixtures of the Stage 6 orchestration tests: one explicit aggregate fixture policy (uniform,
 * deliberately simple thresholds — not a production calibration) and a snapshot builder that
 * parameterises each evidence dimension independently. Snapshots are the contract-valid MFS v2
 * TRADE-triggered snapshot of {@link SignalRuleTestSupport}; the {@link QualityAssessment} is
 * produced by the real Stage 3 resolver, so the whole pipeline is exercised end to end in the domain.
 *
 * <p>Fixture thresholds: flow ±0.30 imbalance (activity 10/5, unknown-side ≤ 0.5); momentum ±2 bps
 * (full 10, safe 50); volatility bands 2 / 8 / 15 (LOW ≤ 2 &lt; NORMAL ≤ 8 &lt; HIGH ≤ 15 &lt;
 * EXTREME); book min 5 levels, top5 ±0.30, microprice ±2 bps (full 10, safe 50).
 */
final class HorizonFixtures {

    static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    static final Instant ASSESSED_AT = EVENT_TIME.plusMillis(100);

    static final QualityEligibilityPolicy QUALITY_POLICY =
            QualityEligibilityPolicy.of(Duration.ofMillis(2_000), Duration.ofMillis(1_000), true);
    static final QualityAssessmentResolver QUALITY_RESOLVER = new QualityAssessmentResolver();

    static final FlowAssessmentPolicy FLOW_POLICY = FlowAssessmentPolicy.of("horizon-flow-v1",
            flowPolicy(H1S), flowPolicy(H5S), flowPolicy(H15S), flowPolicy(H60S));
    static final MomentumAssessmentPolicy MOMENTUM_POLICY = MomentumAssessmentPolicy.of("horizon-momentum-v1",
            momentumPolicy(H5S), momentumPolicy(H15S), momentumPolicy(H60S));
    static final VolatilityAssessmentPolicy VOLATILITY_POLICY = VolatilityAssessmentPolicy.of("horizon-volatility-v1",
            volatilityPolicy(H1S), volatilityPolicy(H5S), volatilityPolicy(H15S), volatilityPolicy(H60S));
    static final BookAssessmentPolicy BOOK_POLICY = new BookAssessmentPolicy("horizon-book-v1", 5,
            bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50"));

    static final HorizonInterpretationPolicy POLICY = new HorizonInterpretationPolicy("horizon-fixture-v1",
            FLOW_POLICY, MOMENTUM_POLICY, VOLATILITY_POLICY, BOOK_POLICY);

    private HorizonFixtures() {
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

    /** A structurally valid BBO with the given microprice offset ({@code null} = whole group absent). */
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

    /** A 5-level book with the given top-5 imbalance ({@code null} = whole group absent). */
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

    // ------------------------------------------------------------------ quality

    /** The real Stage 3 assessment of {@code snapshot} at a fresh {@link #ASSESSED_AT}. */
    static QualityAssessment quality(MarketFeaturesSnapshot snapshot) {
        return QUALITY_RESOLVER.resolve(snapshot, ASSESSED_AT, QUALITY_POLICY);
    }
}
