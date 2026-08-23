package com.trading.marketsignalengine.application.domain.interpretation.flow;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceEligibilityProjection;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.SnapshotQualityConsistencyGuard;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, deterministic Flow V1 evaluator: one independent {@code FLOW} {@link EvidenceAssessment} per
 * {@link MarketHorizon} from a validated {@link MarketFeaturesSnapshot}, the Stage 3
 * {@link QualityAssessment} and an explicit {@link FlowAssessmentPolicy}. No Spring, Kafka, Avro,
 * infrastructure, {@code Clock}, {@code Instant.now()} or metrics; same input + policy ⇒ equal result.
 * The output is heuristic <em>evidence</em> — not a probability, not a confidence, not BUY/SELL and not
 * an opportunity.
 *
 * <h2>Per-horizon pipeline (first match wins)</h2>
 * <ol>
 *   <li><b>Eligibility precedence.</b> The horizon's {@link HorizonEligibility} is read first. A
 *       non-ELIGIBLE horizon is projected without reading any feature value through the shared
 *       {@link EvidenceEligibilityProjection}: direction UNKNOWN, no strength, the eligibility
 *       reasons kept verbatim. Warm-up / missing / untrusted / failed never become NEUTRAL.</li>
 *   <li><b>Window selection.</b> {@link TradeFlowFeature#window(MarketHorizon)} is the single canonical
 *       horizon → window mapping.</li>
 *   <li><b>Missing input → UNAVAILABLE.</b> Absent group or window ({@code FLOW_WINDOW_MISSING}); absent
 *       {@code signedFlowImbalance} ({@code FLOW_IMBALANCE_MISSING}); absent {@code tradeCount},
 *       {@code aggressiveTradeCount} or {@code unknownSideCount} ({@code FLOW_ACTIVITY_COUNTS_MISSING}).
 *       The last two are reported together when both apply. {@code null} is never read as zero.</li>
 *   <li><b>Invalid input → UNTRUSTED.</b> {@code signedFlowImbalance ∉ [-1, 1]}
 *       ({@code FLOW_IMBALANCE_OUT_OF_RANGE}); a negative count, {@code aggressiveTradeCount > tradeCount},
 *       {@code unknownSideCount > tradeCount}, {@code aggressiveTradeCount + unknownSideCount > tradeCount}
 *       (a trade's side is either known or unknown, aggressive trades have a known side), and — when
 *       {@code validQtyTradeCount} is present — a negative value, {@code validQtyTradeCount > tradeCount}
 *       or {@code aggressiveTradeCount > validQtyTradeCount} (aggressive trades have a usable quantity)
 *       ({@code FLOW_ACTIVITY_COUNTS_INVALID}). Both codes are reported when both apply. No direction is
 *       ever derived from invalid values.</li>
 *   <li><b>Activity gate → AVAILABLE / UNKNOWN.</b> {@code tradeCount < minTradeCount} or
 *       {@code aggressiveTradeCount < minAggressiveTradeCount}: the data was computed but carries too
 *       little activity for a directional conclusion ({@code FLOW_INSUFFICIENT_ACTIVITY}); direction
 *       UNKNOWN and <em>no</em> strength — insufficient activity is not NEUTRAL.</li>
 *   <li><b>Unknown-side gate → UNTRUSTED.</b> {@code unknownSideRatio = unknownSideCount / tradeCount}
 *       (see {@link #unknownSideRatio}); {@code ratio > maxUnknownSideRatio} is
 *       {@code FLOW_UNKNOWN_SIDE_RATIO_EXCEEDED}; {@code ratio == max} is still trusted.</li>
 *   <li><b>Direction.</b> {@code imbalance >= bullishImbalanceThreshold → BULLISH}
 *       ({@code FLOW_BULLISH_IMBALANCE}), {@code imbalance <= bearishImbalanceThreshold → BEARISH}
 *       ({@code FLOW_BEARISH_IMBALANCE}), otherwise {@code NEUTRAL} ({@code FLOW_NEUTRAL_IMBALANCE}).
 *       Boundaries are inclusive on the directional side. Strength: {@code |signedFlowImbalance|} for
 *       BULLISH / BEARISH, a real computed {@code 0} for NEUTRAL (absent strength means "could not be
 *       assessed", never "zero").</li>
 * </ol>
 *
 * <p>Deliberate Flow V1 limits: only {@code signedFlowImbalance} and the three activity counts drive the
 * verdict. {@code tradeIntensity}, {@code totalAggressiveVolume}, {@code validQtyTradeCount} (beyond the
 * contradiction check above), {@code signedTradeFlow}, {@code avgTradeSize} and {@code vwap} are not
 * gates and do not weight direction or strength — no thresholds or weights are invented for them
 * without replay. {@code MIXED} is never produced from one aggregate window: alignment, pullback,
 * reversal and cross-horizon conflicts belong to the cross-horizon layer. The snapshot-level quality
 * status ({@code BLOCKED}, {@code eligibleForTrading}) is not re-applied here — it is a fact about the
 * snapshot the opportunity layer enforces, while this evidence is a fact about one eligible window.
 *
 * <p>The snapshot and its {@link QualityAssessment} arrive as two arguments, so the shared
 * {@link SnapshotQualityConsistencyGuard} cross-checks that the assessment was produced from this
 * snapshot — including the full per-horizon eligibilities, re-derived through the canonical
 * resolver — and fails fast on a mismatched pair. The guard runs exactly once per public
 * {@code evaluate(...)} call, never once per horizon.
 */
public final class FlowAssessmentEvaluator {

    private static final BigDecimal MINUS_ONE = BigDecimal.ONE.negate();

    /** Shared snapshot ↔ assessment consistency check; stateless, so the evaluator stays pure and thread-safe. */
    private final SnapshotQualityConsistencyGuard consistencyGuard;

    public FlowAssessmentEvaluator() {
        this(new SnapshotQualityConsistencyGuard());
    }

    /** Package-private for tests; production uses the canonical guard. */
    FlowAssessmentEvaluator(SnapshotQualityConsistencyGuard consistencyGuard) {
        this.consistencyGuard = requireNonNull(consistencyGuard, "consistencyGuard");
    }

    /**
     * Minimum scale of the unknown-side ratio. The effective scale is
     * {@code max(MIN_RATIO_SCALE, scale(maxUnknownSideRatio))} with {@link RoundingMode#CEILING}, so the
     * rounded ratio is {@code >= } the exact one and the gate is exact at the policy boundary: an exact
     * ratio strictly above the maximum never rounds down onto it, and one at or below it never rounds
     * above it.
     */
    static final int MIN_RATIO_SCALE = 6;
    static final RoundingMode RATIO_ROUNDING = RoundingMode.CEILING;

    /** FLOW evidence for all four horizons, in canonical order. The consistency guard runs once. */
    public FlowAssessments evaluate(MarketFeaturesSnapshot snapshot,
                                    QualityAssessment qualityAssessment,
                                    FlowAssessmentPolicy policy) {
        validateInputs(snapshot, qualityAssessment, policy);
        consistencyGuard.verify(snapshot, qualityAssessment);
        Map<MarketHorizon, EvidenceAssessment> result = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            result.put(horizon, evaluateValidated(snapshot, qualityAssessment, policy, horizon));
        }
        return new FlowAssessments(result);
    }

    /** FLOW evidence for one horizon. The consistency guard runs once. */
    public EvidenceAssessment evaluate(MarketFeaturesSnapshot snapshot,
                                       QualityAssessment qualityAssessment,
                                       FlowAssessmentPolicy policy,
                                       MarketHorizon horizon) {
        validateInputs(snapshot, qualityAssessment, policy);
        requireNonNull(horizon, "horizon");
        consistencyGuard.verify(snapshot, qualityAssessment);
        return evaluateValidated(snapshot, qualityAssessment, policy, horizon);
    }

    private static void validateInputs(MarketFeaturesSnapshot snapshot,
                                       QualityAssessment qualityAssessment,
                                       FlowAssessmentPolicy policy) {
        requireNonNull(snapshot, "snapshot");
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(policy, "flow policy");
    }

    /** One horizon after common input validation and the consistency guard; no re-validation here. */
    private EvidenceAssessment evaluateValidated(MarketFeaturesSnapshot snapshot,
                                                 QualityAssessment qualityAssessment,
                                                 FlowAssessmentPolicy policy,
                                                 MarketHorizon horizon) {
        // 1. Eligibility precedence: no feature value is read for a non-ELIGIBLE horizon.
        HorizonEligibility eligibility = qualityAssessment.eligibilityOf(horizon);
        if (!eligibility.isEligible()) {
            return EvidenceEligibilityProjection.project(EvidenceDimension.FLOW, eligibility);
        }
        List<ReasonCode> inherited = eligibility.reasonCodes();

        // 2. Window selection (single canonical mapping).
        TradeFlowFeature tradeFlow = snapshot.tradeFlow();
        TradeFlowWindow window = tradeFlow == null ? null : tradeFlow.window(horizon);
        if (window == null) {
            return EvidenceAssessment.unavailable(EvidenceDimension.FLOW,
                    concat(inherited, FlowReasonCodes.FLOW_WINDOW_MISSING));
        }

        // 3. Missing input → UNAVAILABLE (null is never zero).
        BigDecimal imbalance = window.signedFlowImbalance();
        Integer tradeCount = window.tradeCount();
        Integer aggressiveCount = window.aggressiveTradeCount();
        Integer unknownSideCount = window.unknownSideCount();
        List<ReasonCode> missing = new ArrayList<>(2);
        if (imbalance == null) {
            missing.add(FlowReasonCodes.FLOW_IMBALANCE_MISSING);
        }
        if (tradeCount == null || aggressiveCount == null || unknownSideCount == null) {
            missing.add(FlowReasonCodes.FLOW_ACTIVITY_COUNTS_MISSING);
        }
        if (!missing.isEmpty()) {
            return EvidenceAssessment.unavailable(EvidenceDimension.FLOW, concat(inherited, missing));
        }

        // 4. Invalid input → UNTRUSTED (no direction from corrupt values).
        List<ReasonCode> invalid = new ArrayList<>(2);
        if (imbalance.compareTo(MINUS_ONE) < 0 || imbalance.compareTo(BigDecimal.ONE) > 0) {
            invalid.add(FlowReasonCodes.FLOW_IMBALANCE_OUT_OF_RANGE);
        }
        if (countsInvalid(tradeCount, aggressiveCount, unknownSideCount, window.validQtyTradeCount())) {
            invalid.add(FlowReasonCodes.FLOW_ACTIVITY_COUNTS_INVALID);
        }
        if (!invalid.isEmpty()) {
            return EvidenceAssessment.untrusted(EvidenceDimension.FLOW, concat(inherited, invalid));
        }

        // 5. Activity gate → AVAILABLE, direction UNKNOWN, no strength (not NEUTRAL).
        FlowHorizonPolicy horizonPolicy = policy.of(horizon);
        if (tradeCount < horizonPolicy.minTradeCount() || aggressiveCount < horizonPolicy.minAggressiveTradeCount()) {
            return EvidenceAssessment.available(EvidenceDimension.FLOW, InterpretationDirection.UNKNOWN, null,
                    concat(inherited, FlowReasonCodes.FLOW_INSUFFICIENT_ACTIVITY));
        }

        // 6. Unknown-side gate → UNTRUSTED (ratio == max still passes).
        BigDecimal ratio = unknownSideRatio(unknownSideCount, tradeCount, horizonPolicy.maxUnknownSideRatio());
        if (ratio.compareTo(horizonPolicy.maxUnknownSideRatio()) > 0) {
            return EvidenceAssessment.untrusted(EvidenceDimension.FLOW,
                    concat(inherited, FlowReasonCodes.FLOW_UNKNOWN_SIDE_RATIO_EXCEEDED));
        }

        // 7. Direction (inclusive on the directional side); strength = |imbalance|, NEUTRAL = real 0.
        if (imbalance.compareTo(horizonPolicy.bullishImbalanceThreshold()) >= 0) {
            return EvidenceAssessment.available(EvidenceDimension.FLOW, InterpretationDirection.BULLISH,
                    EvidenceStrength.of(imbalance.abs()), concat(inherited, FlowReasonCodes.FLOW_BULLISH_IMBALANCE));
        }
        if (imbalance.compareTo(horizonPolicy.bearishImbalanceThreshold()) <= 0) {
            return EvidenceAssessment.available(EvidenceDimension.FLOW, InterpretationDirection.BEARISH,
                    EvidenceStrength.of(imbalance.abs()), concat(inherited, FlowReasonCodes.FLOW_BEARISH_IMBALANCE));
        }
        return EvidenceAssessment.available(EvidenceDimension.FLOW, InterpretationDirection.NEUTRAL,
                EvidenceStrength.MIN, concat(inherited, FlowReasonCodes.FLOW_NEUTRAL_IMBALANCE));
    }

    /**
     * Count contradictions per the MFS v2 field semantics: no negative count; {@code aggressive},
     * {@code unknownSide} and {@code aggressive + unknownSide} never exceed {@code tradeCount}; an
     * optional {@code validQtyTradeCount} is non-negative, at most {@code tradeCount} and at least
     * {@code aggressiveTradeCount}. Package-visible for tests.
     */
    static boolean countsInvalid(int tradeCount, int aggressiveCount, int unknownSideCount, Integer validQtyCount) {
        if (tradeCount < 0 || aggressiveCount < 0 || unknownSideCount < 0) {
            return true;
        }
        if (aggressiveCount > tradeCount || unknownSideCount > tradeCount) {
            return true;
        }
        if ((long) aggressiveCount + (long) unknownSideCount > tradeCount) {
            return true;
        }
        if (validQtyCount != null) {
            return validQtyCount < 0 || validQtyCount > tradeCount || aggressiveCount > validQtyCount;
        }
        return false;
    }

    /**
     * {@code unknownSideCount / tradeCount} as an exact-enough decimal: scale
     * {@code max(MIN_RATIO_SCALE, scale(maxUnknownSideRatio))}, {@link RoundingMode#CEILING}. Requires
     * {@code tradeCount > 0} (guaranteed after the activity gate, {@code minTradeCount > 0}).
     * Package-visible for tests.
     */
    static BigDecimal unknownSideRatio(int unknownSideCount, int tradeCount, BigDecimal maxUnknownSideRatio) {
        if (tradeCount <= 0) {
            throw new IllegalArgumentException("unknown-side ratio requires a positive tradeCount, got " + tradeCount);
        }
        int scale = Math.max(MIN_RATIO_SCALE, maxUnknownSideRatio.scale());
        return BigDecimal.valueOf(unknownSideCount).divide(BigDecimal.valueOf(tradeCount), scale, RATIO_ROUNDING);
    }

    private static List<ReasonCode> concat(List<ReasonCode> inherited, ReasonCode code) {
        return concat(inherited, List.of(code));
    }

    /** Inherited eligibility reasons first, then the flow reasons; duplicate-free, insertion-ordered, immutable. */
    private static List<ReasonCode> concat(List<ReasonCode> inherited, List<ReasonCode> flow) {
        Set<ReasonCode> merged = new LinkedHashSet<>(inherited);
        merged.addAll(flow);
        return List.copyOf(merged);
    }
}
