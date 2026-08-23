package com.trading.marketsignalengine.application.domain.interpretation.momentum;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;

import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessmentResolver;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityEligibilityPolicy;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Fixtures of the Momentum V1 tests. The policy is an <b>explicit test fixture</b> with deliberately
 * different thresholds per horizon (so horizon-specific verdicts are observable); it is not a
 * production calibration. Snapshots are the contract-valid MFS v2 TRADE-triggered snapshot of
 * {@link SignalRuleTestSupport} with all four trade-flow windows active (so every horizon is ELIGIBLE
 * at Stage 3) and the regime group replaced per scenario; the {@link QualityAssessment} is produced
 * by the real Stage 3 resolver.
 */
final class MomentumFixtures {

    static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    static final Instant COMPUTED_AT = SignalRuleTestSupport.COMPUTED_AT;
    static final Instant ASSESSED_AT = EVENT_TIME.plusMillis(100);

    static final QualityEligibilityPolicy QUALITY_POLICY =
            QualityEligibilityPolicy.of(Duration.ofMillis(2_000), Duration.ofMillis(1_000), true);
    static final QualityAssessmentResolver QUALITY_RESOLVER = new QualityAssessmentResolver();

    static final String POLICY_VERSION = "momentum-fixture-v1";

    /** 5S: symmetric ±2 bps, full strength at 10 bps, safe up to 50 bps. */
    static final MomentumHorizonPolicy P5S = MomentumHorizonPolicy.of(H5S, bd("2"), bd("-2"), bd("10"), bd("50"));
    /** 15S: asymmetric thresholds on purpose (bullish 3, bearish -4), full 12, safe 60. */
    static final MomentumHorizonPolicy P15S = MomentumHorizonPolicy.of(H15S, bd("3"), bd("-4"), bd("12"), bd("60"));
    /** 60S: wide dead zone ±5, full 20, safe 80. */
    static final MomentumHorizonPolicy P60S = MomentumHorizonPolicy.of(H60S, bd("5"), bd("-5"), bd("20"), bd("80"));

    static final MomentumAssessmentPolicy POLICY = MomentumAssessmentPolicy.of(POLICY_VERSION, P5S, P15S, P60S);

    private MomentumFixtures() {
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------ snapshots

    /** A fully computed, active trade-flow window so Stage 3 marks every horizon ELIGIBLE. */
    static TradeFlowWindow activeWindow() {
        return TradeFlowWindow.builder()
                .signedFlowImbalance(bd("0.00"))
                .tradeCount(200)
                .validQtyTradeCount(200)
                .aggressiveTradeCount(150)
                .unknownSideCount(0)
                .tradeIntensity(bd("3.0"))
                .build();
    }

    static TradeFlowFeature activeTradeFlow() {
        TradeFlowWindow window = activeWindow();
        return TradeFlowFeature.builder()
                .window1s(window).window5s(window).window15s(window).window60s(window)
                .build();
    }

    /** A regime group with the given signed price changes ({@code null} = absent value). */
    static RegimeFeature regime(String priceChangeBps5s, String priceChangeBps15s, String priceChangeBps60s) {
        return RegimeFeature.builder()
                .realizedVolatilityBps1s(bd("5.0"))
                .priceChangeBps5s(priceChangeBps5s == null ? null : bd(priceChangeBps5s))
                .priceChangeBps15s(priceChangeBps15s == null ? null : bd(priceChangeBps15s))
                .priceChangeBps60s(priceChangeBps60s == null ? null : bd(priceChangeBps60s))
                .build();
    }

    /** The same price change on every scoped horizon. */
    static RegimeFeature uniformRegime(String priceChangeBps) {
        return regime(priceChangeBps, priceChangeBps, priceChangeBps);
    }

    static MarketFeaturesSnapshot snapshot(RegimeFeature regime) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .regime(regime)
                .build();
    }

    // ------------------------------------------------------------------ quality

    /** The real Stage 3 assessment of {@code snapshot} at a fresh {@link #ASSESSED_AT}. */
    static QualityAssessment quality(MarketFeaturesSnapshot snapshot) {
        return QUALITY_RESOLVER.resolve(snapshot, ASSESSED_AT, QUALITY_POLICY);
    }
}
