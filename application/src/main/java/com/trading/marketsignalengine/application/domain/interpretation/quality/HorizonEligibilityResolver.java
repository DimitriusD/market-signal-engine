package com.trading.marketsignalengine.application.domain.interpretation.quality;

import com.trading.marketsignalengine.application.domain.availability.FeatureAvailabilityResolver;
import com.trading.marketsignalengine.application.domain.availability.FeatureWindowAvailability;
import com.trading.marketsignalengine.application.domain.availability.TradeFlowAvailability;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure per-horizon eligibility resolver: one {@link HorizonEligibility} for each of
 * {@code 1S / 5S / 15S / 60S}, decided independently, from the input-side trade-flow availability of
 * {@link FeatureAvailabilityResolver}. At this stage a horizon's eligibility is backed by its
 * trade-flow window (the only per-horizon feature group MFS v2 publishes); BOOK / MOMENTUM /
 * VOLATILITY evidence availability is the evaluators' concern (next stage) and does not make a horizon
 * ineligible here.
 *
 * <h2>Mapping</h2>
 * <pre>
 *   source quality NO_DATA           → every horizon UNAVAILABLE   [SOURCE_NO_DATA]     (checked first)
 *   availability AVAILABLE           → ELIGIBLE                    []   (WINDOW_COMPUTED is not a reason)
 *   availability WARMING_UP          → WARMING_UP                  [WINDOW_WARMING_UP]
 *   availability UNAVAILABLE         → UNAVAILABLE                 [WINDOW_NOT_COMPUTED | TRADE_FLOW_GROUP_ABSENT]
 *   availability UNTRUSTED           → UNTRUSTED                   [STALE_TRADES | TRADE_HISTORY_GAP]
 *   availability FAILED              → FAILED                      [TRADE_FLOW_CALCULATOR_FAILED]
 * </pre>
 * Consequences inherited from the availability precedence: a {@code trade-flow} calculator failure
 * fails all four horizons; {@code staleTrades} makes all four UNTRUSTED; a trade-history gap makes only
 * the uncovered (longer) horizons UNTRUSTED while already-computed shorter ones stay ELIGIBLE; global
 * warm-up makes only the not-yet-computed horizons WARMING_UP (computed 1S/5S stay ELIGIBLE); an absent
 * trade-flow group / window is UNAVAILABLE, never a zero or NEUTRAL reading. {@code NO_DATA} is
 * UNAVAILABLE on every horizon even when the producer also set {@code staleTrades} — absent data is
 * not "untrusted data". Failures of {@code bbo} / {@code order-book} / {@code short-term-regime} do
 * not touch trade-flow horizons (per-feature degradation; they are reported by the quality
 * assessment instead). The known 1S/5S covered-but-empty limitation of the availability resolver is
 * kept as is (conservative, not guessed).
 */
public final class HorizonEligibilityResolver {

    private final FeatureAvailabilityResolver availabilityResolver;

    public HorizonEligibilityResolver() {
        this(new FeatureAvailabilityResolver());
    }

    public HorizonEligibilityResolver(FeatureAvailabilityResolver availabilityResolver) {
        this.availabilityResolver = Objects.requireNonNull(availabilityResolver, "availabilityResolver");
    }

    public HorizonEligibilities resolve(MarketFeaturesSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        FeatureQuality quality = Objects.requireNonNull(snapshot.quality(), "snapshot.quality");
        FeatureQualityStatus sourceStatus = Objects.requireNonNull(quality.status(), "snapshot.quality.status");

        if (sourceStatus == FeatureQualityStatus.NO_DATA) {
            return HorizonEligibilities.uniform(
                    HorizonEligibility.unavailable(List.of(QualityReasonCodes.SOURCE_NO_DATA)));
        }

        TradeFlowAvailability availability = availabilityResolver.resolveTradeFlow(snapshot);
        Map<MarketHorizon, HorizonEligibility> result = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            result.put(horizon, toEligibility(availability.of(horizon)));
        }
        return new HorizonEligibilities(result);
    }

    /** Availability → eligibility for one horizon; the only place the two vocabularies meet. */
    static HorizonEligibility toEligibility(FeatureWindowAvailability availability) {
        Objects.requireNonNull(availability, "availability");
        return switch (availability.status()) {
            case AVAILABLE -> HorizonEligibility.eligible();
            case WARMING_UP -> HorizonEligibility.warmingUp(reasonsOf(availability));
            case UNAVAILABLE -> HorizonEligibility.unavailable(reasonsOf(availability));
            case UNTRUSTED -> HorizonEligibility.untrusted(reasonsOf(availability));
            case FAILED -> HorizonEligibility.failed(reasonsOf(availability));
        };
    }

    private static List<ReasonCode> reasonsOf(FeatureWindowAvailability availability) {
        List<ReasonCode> reasons = new ArrayList<>(availability.reasonCodes().size());
        for (String code : availability.reasonCodes()) {
            if (FeatureAvailabilityResolver.CODE_WINDOW_COMPUTED.equals(code)) {
                continue; // a fact about availability, not a reason for a non-eligible verdict
            }
            reasons.add(ReasonCode.of(code));
        }
        return reasons;
    }
}
