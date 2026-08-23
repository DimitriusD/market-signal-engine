package com.trading.marketsignalengine.application.domain.interpretation.flow;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;

import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQuality;
import com.trading.marketsignalengine.application.domain.interpretation.quality.HorizonEligibilities;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessmentResolver;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityEligibilityPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityReasonCodes;
import com.trading.marketsignalengine.application.domain.interpretation.quality.TimingAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.TimingStatus;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Fixtures of the Flow V1 tests. The policy is an <b>explicit test fixture</b> with deliberately
 * different thresholds per horizon (so horizon-specific verdicts are observable); it is not a
 * production calibration. Snapshots are the contract-valid MFS v2 TRADE-triggered snapshot of
 * {@link SignalRuleTestSupport} with the trade-flow group replaced per scenario; the
 * {@link QualityAssessment} is produced by the real Stage 3 resolver unless a test builds one by hand.
 */
final class FlowFixtures {

    static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    static final Instant COMPUTED_AT = SignalRuleTestSupport.COMPUTED_AT;
    static final Instant ASSESSED_AT = EVENT_TIME.plusMillis(100);

    static final QualityEligibilityPolicy QUALITY_POLICY =
            QualityEligibilityPolicy.of(Duration.ofMillis(2_000), Duration.ofMillis(1_000), true);
    static final QualityAssessmentResolver QUALITY_RESOLVER = new QualityAssessmentResolver();

    static final String POLICY_VERSION = "flow-fixture-v1";

    /** 1S: wide dead zone, tiny activity floor, tolerant of unknown sides. */
    static final FlowHorizonPolicy P1S = FlowHorizonPolicy.of(H1S, bd("0.60"), bd("-0.60"), 3, 2, bd("0.50"));
    /** 5S: the V1-like trigger window. */
    static final FlowHorizonPolicy P5S = FlowHorizonPolicy.of(H5S, bd("0.30"), bd("-0.30"), 10, 5, bd("0.25"));
    /** 15S: asymmetric thresholds on purpose (bullish 0.20, bearish -0.25). */
    static final FlowHorizonPolicy P15S = FlowHorizonPolicy.of(H15S, bd("0.20"), bd("-0.25"), 30, 10, bd("0.20"));
    /** 60S: narrow dead zone, high activity floor, strict unknown-side limit. */
    static final FlowHorizonPolicy P60S = FlowHorizonPolicy.of(H60S, bd("0.15"), bd("-0.15"), 100, 40, bd("0.10"));

    static final FlowAssessmentPolicy POLICY = FlowAssessmentPolicy.of(POLICY_VERSION, P1S, P5S, P15S, P60S);

    private FlowFixtures() {
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------ windows / snapshots

    /** A fully computed window: counts consistent, {@code validQtyTradeCount = tradeCount}. */
    static TradeFlowWindow window(String imbalance, int tradeCount, int aggressiveCount, int unknownSideCount) {
        return TradeFlowWindow.builder()
                .signedFlowImbalance(imbalance == null ? null : bd(imbalance))
                .tradeCount(tradeCount)
                .validQtyTradeCount(tradeCount)
                .aggressiveTradeCount(aggressiveCount)
                .unknownSideCount(unknownSideCount)
                .tradeIntensity(bd("3.0"))
                .build();
    }

    /** A window active enough for every fixture horizon (tradeCount 200, aggressive 150, unknown 0). */
    static TradeFlowWindow active(String imbalance) {
        return window(imbalance, 200, 150, 0);
    }

    static TradeFlowFeature tradeFlow(TradeFlowWindow w1s, TradeFlowWindow w5s, TradeFlowWindow w15s, TradeFlowWindow w60s) {
        return TradeFlowFeature.builder().window1s(w1s).window5s(w5s).window15s(w15s).window60s(w60s).build();
    }

    /** The same window on every horizon. */
    static TradeFlowFeature uniform(TradeFlowWindow window) {
        return tradeFlow(window, window, window, window);
    }

    /** Replaces only {@code horizon}'s window; the other three are {@code active("0.00")}. */
    static TradeFlowFeature withWindow(MarketHorizon horizon, TradeFlowWindow window) {
        TradeFlowWindow base = active("0.00");
        return tradeFlow(
                horizon == H1S ? window : base,
                horizon == H5S ? window : base,
                horizon == H15S ? window : base,
                horizon == H60S ? window : base);
    }

    static MarketFeaturesSnapshot snapshot(TradeFlowFeature tradeFlow) {
        return SignalRuleTestSupport.tradableFeaturesBuilder().tradeFlow(tradeFlow).build();
    }

    // ------------------------------------------------------------------ quality

    /** The real Stage 3 assessment of {@code snapshot} at a fresh {@link #ASSESSED_AT}. */
    static QualityAssessment quality(MarketFeaturesSnapshot snapshot) {
        return QUALITY_RESOLVER.resolve(snapshot, ASSESSED_AT, QUALITY_POLICY);
    }

    /** A hand-built, structurally valid assessment with the given per-horizon eligibilities and fresh timing. */
    static QualityAssessment qualityWith(HorizonEligibilities eligibilities) {
        InterpretationQuality overall;
        if (eligibilities.allEligible()) {
            overall = InterpretationQuality.ok(List.of());
        } else if (eligibilities.anyEligible()) {
            overall = InterpretationQuality.degraded(true, List.of(QualityReasonCodes.HORIZONS_PARTIALLY_ELIGIBLE));
        } else {
            overall = InterpretationQuality.degraded(false, List.of(QualityReasonCodes.NO_ELIGIBLE_HORIZONS));
        }
        TimingAssessment fresh = new TimingAssessment(ASSESSED_AT, EVENT_TIME, COMPUTED_AT,
                ASSESSED_AT.toEpochMilli() - EVENT_TIME.toEpochMilli(),
                ASSESSED_AT.toEpochMilli() - COMPUTED_AT.toEpochMilli(),
                TimingStatus.FRESH, List.of());
        return new QualityAssessment(FeatureQualityStatus.OK, overall, fresh, eligibilities, List.of(), false);
    }

    static QualityAssessment allEligible() {
        return qualityWith(HorizonEligibilities.uniform(HorizonEligibility.eligible()));
    }
}
