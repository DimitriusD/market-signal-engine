package com.trading.marketsignalengine.application.domain.interpretation.momentum;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceEligibilityProjection;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.quality.FeatureGroupId;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.SnapshotQualityConsistencyGuard;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.RegimeFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, deterministic Momentum V1 evaluator: one independent {@code MOMENTUM}
 * {@link EvidenceAssessment} per {@link MarketHorizon} from a validated
 * {@link MarketFeaturesSnapshot}, the Stage 3 {@link QualityAssessment} and an explicit
 * {@link MomentumAssessmentPolicy}. No Spring, Kafka, Avro, infrastructure, {@code Clock},
 * {@code Instant.now()} or metrics; same input + policy ⇒ equal result. The output is heuristic
 * <em>evidence</em> — not a probability, not a confidence, not BUY/SELL and not an opportunity.
 *
 * <h2>Horizon → feature (single canonical selector, {@link #priceChangeBpsOf})</h2>
 * <pre>
 *   1S  → (not published by MFS v2)          → UNAVAILABLE [MOMENTUM_NOT_SCOPED_TO_HORIZON]
 *   5S  → RegimeFeature.priceChangeBps5s()
 *   15S → RegimeFeature.priceChangeBps15s()
 *   60S → RegimeFeature.priceChangeBps60s()
 * </pre>
 * The 1S horizon is never approximated by the 5S value — a 1S reading MFS v2 does not publish would
 * be an invented feature.
 *
 * <h2>Per-horizon pipeline (first match wins)</h2>
 * <ol>
 *   <li><b>Eligibility precedence.</b> A non-ELIGIBLE horizon is projected through the shared
 *       {@link EvidenceEligibilityProjection} without reading any feature value: direction UNKNOWN,
 *       no strength, the eligibility reasons kept verbatim. Warm-up / missing / untrusted / failed
 *       never become NEUTRAL.</li>
 *   <li><b>1S not scoped → UNAVAILABLE.</b> An eligible 1S horizon still has no momentum feature
 *       ({@code MOMENTUM_NOT_SCOPED_TO_HORIZON}).</li>
 *   <li><b>Failed regime calculator → FAILED.</b> {@code short-term-regime} among the failed feature
 *       groups ({@code MOMENTUM_REGIME_CALCULATOR_FAILED}); the horizon may be eligible (eligibility
 *       is trade-flow-backed at Stage 3), but the momentum input group is broken.</li>
 *   <li><b>Missing input → UNAVAILABLE.</b> Absent regime group ({@code MOMENTUM_REGIME_MISSING});
 *       absent {@code priceChangeBps*s} ({@code MOMENTUM_PRICE_CHANGE_MISSING}). {@code null} is
 *       never read as a zero move.</li>
 *   <li><b>Invalid input → UNTRUSTED.</b> {@code abs(priceChangeBps) > maxSafeAbsMoveBps}
 *       ({@code MOMENTUM_PRICE_CHANGE_OUT_OF_SAFE_RANGE}); the boundary itself is still trusted. No
 *       direction is ever derived from an implausible move.</li>
 *   <li><b>Direction.</b> {@code priceChangeBps >= bullishPriceChangeBpsThreshold → BULLISH}
 *       ({@code MOMENTUM_BULLISH_MOVE}), {@code <= bearishPriceChangeBpsThreshold → BEARISH}
 *       ({@code MOMENTUM_BEARISH_MOVE}), otherwise {@code NEUTRAL} ({@code MOMENTUM_NEUTRAL_MOVE}).
 *       Boundaries are inclusive on the directional side. Strength:
 *       {@code min(1, abs(priceChangeBps) / fullStrengthAbsMoveBps)} for BULLISH / BEARISH (see
 *       {@link #saturatingStrength}), a real computed {@code 0} for NEUTRAL (absent strength means
 *       "could not be assessed", never "zero").</li>
 * </ol>
 *
 * <p>Deliberate Momentum V1 limits: only the horizon's own {@code priceChangeBps*s} drives the
 * verdict. {@code lastTradeDistanceToMidBps}, VWAP, trade flow, flow confirmation / divergence,
 * absorption, exhaustion, volatility, book evidence and cross-horizon values are not read — momentum
 * answers only "what is the direction and strength of the price move on this horizon"; confirmation
 * and divergence belong to the next interpretation layer.
 *
 * <p>The shared {@link SnapshotQualityConsistencyGuard} cross-checks that the assessment was produced
 * from this snapshot and runs exactly once per public {@code evaluate(...)} call, never once per
 * horizon.
 */
public final class MomentumAssessmentEvaluator {

    /**
     * Scale and rounding of the strength ratio {@code abs(move) / fullStrengthAbsMoveBps}.
     * {@link RoundingMode#DOWN} never inflates the exact ratio, so a move strictly below full
     * strength always yields a strength strictly below 1 and saturation happens exactly at
     * {@code abs(move) >= fullStrengthAbsMoveBps}.
     */
    static final int STRENGTH_SCALE = 6;
    static final RoundingMode STRENGTH_ROUNDING = RoundingMode.DOWN;

    /** Shared snapshot ↔ assessment consistency check; stateless, so the evaluator stays pure and thread-safe. */
    private final SnapshotQualityConsistencyGuard consistencyGuard;

    public MomentumAssessmentEvaluator() {
        this(new SnapshotQualityConsistencyGuard());
    }

    /** Package-private for tests; production uses the canonical guard. */
    MomentumAssessmentEvaluator(SnapshotQualityConsistencyGuard consistencyGuard) {
        this.consistencyGuard = requireNonNull(consistencyGuard, "consistencyGuard");
    }

    /** MOMENTUM evidence for all four horizons, in canonical order. The consistency guard runs once. */
    public MomentumAssessments evaluate(MarketFeaturesSnapshot snapshot,
                                        QualityAssessment qualityAssessment,
                                        MomentumAssessmentPolicy policy) {
        validateInputs(snapshot, qualityAssessment, policy);
        consistencyGuard.verify(snapshot, qualityAssessment);
        Map<MarketHorizon, EvidenceAssessment> result = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            result.put(horizon, evaluateValidated(snapshot, qualityAssessment, policy, horizon));
        }
        return new MomentumAssessments(result);
    }

    /** MOMENTUM evidence for one horizon. The consistency guard runs once. */
    public EvidenceAssessment evaluate(MarketFeaturesSnapshot snapshot,
                                       QualityAssessment qualityAssessment,
                                       MomentumAssessmentPolicy policy,
                                       MarketHorizon horizon) {
        validateInputs(snapshot, qualityAssessment, policy);
        requireNonNull(horizon, "horizon");
        consistencyGuard.verify(snapshot, qualityAssessment);
        return evaluateValidated(snapshot, qualityAssessment, policy, horizon);
    }

    private static void validateInputs(MarketFeaturesSnapshot snapshot,
                                       QualityAssessment qualityAssessment,
                                       MomentumAssessmentPolicy policy) {
        requireNonNull(snapshot, "snapshot");
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(policy, "momentum policy");
    }

    /** One horizon after common input validation and the consistency guard; no re-validation here. */
    private static EvidenceAssessment evaluateValidated(MarketFeaturesSnapshot snapshot,
                                                        QualityAssessment qualityAssessment,
                                                        MomentumAssessmentPolicy policy,
                                                        MarketHorizon horizon) {
        // 1. Eligibility precedence: no feature value is read for a non-ELIGIBLE horizon.
        HorizonEligibility eligibility = qualityAssessment.eligibilityOf(horizon);
        if (!eligibility.isEligible()) {
            return EvidenceEligibilityProjection.project(EvidenceDimension.MOMENTUM, eligibility);
        }
        List<ReasonCode> inherited = eligibility.reasonCodes();

        // 2. 1S is not scoped: MFS v2 publishes no 1S price change; 5S is never substituted.
        if (horizon == MarketHorizon.H1S) {
            return EvidenceAssessment.unavailable(EvidenceDimension.MOMENTUM,
                    concat(inherited, MomentumReasonCodes.MOMENTUM_NOT_SCOPED_TO_HORIZON));
        }

        // 3. Failed regime calculator → FAILED.
        if (qualityAssessment.hasFailedFeatureGroup(FeatureGroupId.SHORT_TERM_REGIME)) {
            return EvidenceAssessment.failed(EvidenceDimension.MOMENTUM,
                    concat(inherited, MomentumReasonCodes.MOMENTUM_REGIME_CALCULATOR_FAILED));
        }

        // 4. Missing input → UNAVAILABLE (null is never a zero move).
        RegimeFeature regime = snapshot.regime();
        if (regime == null) {
            return EvidenceAssessment.unavailable(EvidenceDimension.MOMENTUM,
                    concat(inherited, MomentumReasonCodes.MOMENTUM_REGIME_MISSING));
        }
        BigDecimal priceChangeBps = priceChangeBpsOf(regime, horizon);
        if (priceChangeBps == null) {
            return EvidenceAssessment.unavailable(EvidenceDimension.MOMENTUM,
                    concat(inherited, MomentumReasonCodes.MOMENTUM_PRICE_CHANGE_MISSING));
        }

        // 5. Implausible move → UNTRUSTED (abs == maxSafe is still trusted).
        MomentumHorizonPolicy horizonPolicy = policy.of(horizon);
        if (priceChangeBps.abs().compareTo(horizonPolicy.maxSafeAbsMoveBps()) > 0) {
            return EvidenceAssessment.untrusted(EvidenceDimension.MOMENTUM,
                    concat(inherited, MomentumReasonCodes.MOMENTUM_PRICE_CHANGE_OUT_OF_SAFE_RANGE));
        }

        // 6. Direction (inclusive on the directional side); strength = min(1, abs / full), NEUTRAL = real 0.
        if (priceChangeBps.compareTo(horizonPolicy.bullishPriceChangeBpsThreshold()) >= 0) {
            return EvidenceAssessment.available(EvidenceDimension.MOMENTUM, InterpretationDirection.BULLISH,
                    saturatingStrength(priceChangeBps.abs(), horizonPolicy.fullStrengthAbsMoveBps()),
                    concat(inherited, MomentumReasonCodes.MOMENTUM_BULLISH_MOVE));
        }
        if (priceChangeBps.compareTo(horizonPolicy.bearishPriceChangeBpsThreshold()) <= 0) {
            return EvidenceAssessment.available(EvidenceDimension.MOMENTUM, InterpretationDirection.BEARISH,
                    saturatingStrength(priceChangeBps.abs(), horizonPolicy.fullStrengthAbsMoveBps()),
                    concat(inherited, MomentumReasonCodes.MOMENTUM_BEARISH_MOVE));
        }
        return EvidenceAssessment.available(EvidenceDimension.MOMENTUM, InterpretationDirection.NEUTRAL,
                EvidenceStrength.MIN, concat(inherited, MomentumReasonCodes.MOMENTUM_NEUTRAL_MOVE));
    }

    /**
     * The <b>single canonical</b> horizon → price-change selection of the momentum dimension
     * ({@code 5S → priceChangeBps5s, 15S → priceChangeBps15s, 60S → priceChangeBps60s});
     * {@code null} when that value is absent. 1S fails fast — MFS v2 publishes no 1S price change and
     * the evaluator answers 1S before ever selecting a feature; substituting the 5S value would
     * invent a reading. Package-visible for tests.
     */
    static BigDecimal priceChangeBpsOf(RegimeFeature regime, MarketHorizon horizon) {
        requireNonNull(regime, "regime");
        requireNonNull(horizon, "horizon");
        return switch (horizon) {
            case H1S -> throw new IllegalArgumentException(
                    "momentum is not scoped to 1S (no priceChangeBps1s in MFS v2)");
            case H5S -> regime.priceChangeBps5s();
            case H15S -> regime.priceChangeBps15s();
            case H60S -> regime.priceChangeBps60s();
        };
    }

    /**
     * {@code min(1, absMoveBps / fullStrengthAbsMoveBps)} as an exact-enough decimal (scale
     * {@link #STRENGTH_SCALE}, {@link RoundingMode#DOWN}): saturates to {@link EvidenceStrength#MAX}
     * exactly at and beyond full strength. Package-visible for tests.
     */
    static EvidenceStrength saturatingStrength(BigDecimal absMoveBps, BigDecimal fullStrengthAbsMoveBps) {
        if (absMoveBps.compareTo(fullStrengthAbsMoveBps) >= 0) {
            return EvidenceStrength.MAX;
        }
        return EvidenceStrength.of(absMoveBps.divide(fullStrengthAbsMoveBps, STRENGTH_SCALE, STRENGTH_ROUNDING));
    }

    private static List<ReasonCode> concat(List<ReasonCode> inherited, ReasonCode code) {
        Set<ReasonCode> merged = new LinkedHashSet<>(inherited);
        merged.add(code);
        return List.copyOf(merged);
    }
}
