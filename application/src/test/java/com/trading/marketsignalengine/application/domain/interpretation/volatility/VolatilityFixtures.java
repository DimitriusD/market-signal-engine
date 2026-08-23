package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
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
 * Fixtures of the Volatility V1 tests. The policy is an <b>explicit test fixture</b> with
 * deliberately different bounds per horizon (so horizon-specific classifications are observable); it
 * is not a production calibration. Snapshots are the contract-valid MFS v2 TRADE-triggered snapshot
 * of {@link SignalRuleTestSupport} with all four trade-flow windows active (so every horizon is
 * ELIGIBLE at Stage 3) and the regime group replaced per scenario; the {@link QualityAssessment} is
 * produced by the real Stage 3 resolver.
 */
final class VolatilityFixtures {

    static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    static final Instant COMPUTED_AT = SignalRuleTestSupport.COMPUTED_AT;
    static final Instant ASSESSED_AT = EVENT_TIME.plusMillis(100);

    static final QualityEligibilityPolicy QUALITY_POLICY =
            QualityEligibilityPolicy.of(Duration.ofMillis(2_000), Duration.ofMillis(1_000), true);
    static final QualityAssessmentResolver QUALITY_RESOLVER = new QualityAssessmentResolver();

    static final String POLICY_VERSION = "volatility-fixture-v1";

    static final VolatilityHorizonPolicy P1S = VolatilityHorizonPolicy.of(H1S, bd("2"), bd("6"), bd("12"));
    static final VolatilityHorizonPolicy P5S = VolatilityHorizonPolicy.of(H5S, bd("3"), bd("8"), bd("15"));
    static final VolatilityHorizonPolicy P15S = VolatilityHorizonPolicy.of(H15S, bd("4"), bd("10"), bd("20"));
    static final VolatilityHorizonPolicy P60S = VolatilityHorizonPolicy.of(H60S, bd("5"), bd("12"), bd("25"));

    static final VolatilityAssessmentPolicy POLICY =
            VolatilityAssessmentPolicy.of(POLICY_VERSION, P1S, P5S, P15S, P60S);

    private VolatilityFixtures() {
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

    /** A regime group with the given realized volatilities ({@code null} = absent value). */
    static RegimeFeature regime(String vol1s, String vol5s, String vol15s, String vol60s) {
        return RegimeFeature.builder()
                .realizedVolatilityBps1s(vol1s == null ? null : bd(vol1s))
                .realizedVolatilityBps5s(vol5s == null ? null : bd(vol5s))
                .realizedVolatilityBps15s(vol15s == null ? null : bd(vol15s))
                .realizedVolatilityBps60s(vol60s == null ? null : bd(vol60s))
                .build();
    }

    /** The same realized volatility on every horizon. */
    static RegimeFeature uniformRegime(String volatilityBps) {
        return regime(volatilityBps, volatilityBps, volatilityBps, volatilityBps);
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
