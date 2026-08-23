package com.trading.marketsignalengine.application.domain.interpretation.volatility;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceEligibilityProjection;
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
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, deterministic Volatility V1 evaluator: one independent typed {@link VolatilityAssessment} per
 * {@link MarketHorizon} from a validated {@link MarketFeaturesSnapshot}, the Stage 3
 * {@link QualityAssessment} and an explicit {@link VolatilityAssessmentPolicy}. No Spring, Kafka,
 * Avro, infrastructure, {@code Clock}, {@code Instant.now()} or metrics; same input + policy ⇒ equal
 * result. The output is a regime <em>classification</em>, not a directional vote — AVAILABLE
 * volatility evidence always reads direction {@link InterpretationDirection#UNKNOWN} with no
 * strength, and HIGH / EXTREME neither block a horizon nor create a NO_TRADE here (context for the
 * future regime / opportunity layer).
 *
 * <h2>Horizon → feature (single canonical selector, {@link #realizedVolatilityBpsOf})</h2>
 * <pre>
 *   1S  → RegimeFeature.realizedVolatilityBps1s()
 *   5S  → RegimeFeature.realizedVolatilityBps5s()
 *   15S → RegimeFeature.realizedVolatilityBps15s()
 *   60S → RegimeFeature.realizedVolatilityBps60s()
 * </pre>
 * The deprecated upstream alias ({@code shortTermVolatility1s}) is not mapped and not read.
 *
 * <h2>Per-horizon pipeline (first match wins)</h2>
 * <ol>
 *   <li><b>Eligibility precedence.</b> A non-ELIGIBLE horizon is projected through the shared
 *       {@link EvidenceEligibilityProjection} without reading any feature value; level UNKNOWN,
 *       the eligibility reasons kept verbatim.</li>
 *   <li><b>Failed regime calculator → FAILED.</b> {@code short-term-regime} among the failed feature
 *       groups ({@code VOLATILITY_REGIME_CALCULATOR_FAILED}).</li>
 *   <li><b>Missing input → UNAVAILABLE.</b> Absent regime group ({@code VOLATILITY_REGIME_MISSING});
 *       absent {@code realizedVolatilityBps*s} ({@code VOLATILITY_VALUE_MISSING}). {@code null} is
 *       never read as zero volatility.</li>
 *   <li><b>Invalid input → UNTRUSTED.</b> A negative value ({@code VOLATILITY_NEGATIVE}) — a realized
 *       volatility magnitude cannot be negative. No artificial NaN / infinity checks: those values
 *       cannot exist in a {@link BigDecimal}.</li>
 *   <li><b>Classification (inclusive upper boundaries).</b> {@code value <= lowUpperBoundBps → LOW},
 *       {@code <= normalUpperBoundBps → NORMAL}, {@code <= highUpperBoundBps → HIGH}, above →
 *       EXTREME; one level reason code per verdict. The level is typed
 *       ({@link VolatilityLevel}), never parsed back out of the codes.</li>
 * </ol>
 *
 * <p>The shared {@link SnapshotQualityConsistencyGuard} cross-checks that the assessment was produced
 * from this snapshot and runs exactly once per public {@code evaluate(...)} call, never once per
 * horizon.
 */
public final class VolatilityAssessmentEvaluator {

    /** Shared snapshot ↔ assessment consistency check; stateless, so the evaluator stays pure and thread-safe. */
    private final SnapshotQualityConsistencyGuard consistencyGuard;

    public VolatilityAssessmentEvaluator() {
        this(new SnapshotQualityConsistencyGuard());
    }

    /** Production uses the canonical guard; visible so a composing evaluator or test can share / instrument one. */
    public VolatilityAssessmentEvaluator(SnapshotQualityConsistencyGuard consistencyGuard) {
        this.consistencyGuard = requireNonNull(consistencyGuard, "consistencyGuard");
    }

    /** VOLATILITY assessments for all four horizons, in canonical order. The consistency guard runs once. */
    public VolatilityAssessments evaluate(MarketFeaturesSnapshot snapshot,
                                          QualityAssessment qualityAssessment,
                                          VolatilityAssessmentPolicy policy) {
        validateInputs(snapshot, qualityAssessment, policy);
        consistencyGuard.verify(snapshot, qualityAssessment);
        Map<MarketHorizon, VolatilityAssessment> result = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            result.put(horizon, evaluateValidated(snapshot, qualityAssessment, policy, horizon));
        }
        return new VolatilityAssessments(result);
    }

    /** The VOLATILITY assessment of one horizon. The consistency guard runs once. */
    public VolatilityAssessment evaluate(MarketFeaturesSnapshot snapshot,
                                         QualityAssessment qualityAssessment,
                                         VolatilityAssessmentPolicy policy,
                                         MarketHorizon horizon) {
        validateInputs(snapshot, qualityAssessment, policy);
        requireNonNull(horizon, "horizon");
        consistencyGuard.verify(snapshot, qualityAssessment);
        return evaluateValidated(snapshot, qualityAssessment, policy, horizon);
    }

    private static void validateInputs(MarketFeaturesSnapshot snapshot,
                                       QualityAssessment qualityAssessment,
                                       VolatilityAssessmentPolicy policy) {
        requireNonNull(snapshot, "snapshot");
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(policy, "volatility policy");
    }

    /** One horizon after common input validation and the consistency guard; no re-validation here. */
    private static VolatilityAssessment evaluateValidated(MarketFeaturesSnapshot snapshot,
                                                          QualityAssessment qualityAssessment,
                                                          VolatilityAssessmentPolicy policy,
                                                          MarketHorizon horizon) {
        // 1. Eligibility precedence: no feature value is read for a non-ELIGIBLE horizon.
        HorizonEligibility eligibility = qualityAssessment.eligibilityOf(horizon);
        if (!eligibility.isEligible()) {
            return VolatilityAssessment.projected(eligibility);
        }
        List<ReasonCode> inherited = eligibility.reasonCodes();

        // 2. Failed regime calculator → FAILED.
        if (qualityAssessment.hasFailedFeatureGroup(FeatureGroupId.SHORT_TERM_REGIME)) {
            return VolatilityAssessment.notAvailable(EvidenceAvailabilityStatus.FAILED,
                    concat(inherited, VolatilityReasonCodes.VOLATILITY_REGIME_CALCULATOR_FAILED));
        }

        // 3. Missing input → UNAVAILABLE (null is never zero volatility).
        RegimeFeature regime = snapshot.regime();
        if (regime == null) {
            return VolatilityAssessment.notAvailable(EvidenceAvailabilityStatus.UNAVAILABLE,
                    concat(inherited, VolatilityReasonCodes.VOLATILITY_REGIME_MISSING));
        }
        BigDecimal value = realizedVolatilityBpsOf(regime, horizon);
        if (value == null) {
            return VolatilityAssessment.notAvailable(EvidenceAvailabilityStatus.UNAVAILABLE,
                    concat(inherited, VolatilityReasonCodes.VOLATILITY_VALUE_MISSING));
        }

        // 4. Invalid input → UNTRUSTED (a magnitude cannot be negative).
        if (value.signum() < 0) {
            return VolatilityAssessment.notAvailable(EvidenceAvailabilityStatus.UNTRUSTED,
                    concat(inherited, VolatilityReasonCodes.VOLATILITY_NEGATIVE));
        }

        // 5. Classification with inclusive upper boundaries; the level is typed, the code is explanation.
        VolatilityHorizonPolicy horizonPolicy = policy.of(horizon);
        if (value.compareTo(horizonPolicy.lowUpperBoundBps()) <= 0) {
            return VolatilityAssessment.available(VolatilityLevel.LOW,
                    concat(inherited, VolatilityReasonCodes.VOLATILITY_LOW));
        }
        if (value.compareTo(horizonPolicy.normalUpperBoundBps()) <= 0) {
            return VolatilityAssessment.available(VolatilityLevel.NORMAL,
                    concat(inherited, VolatilityReasonCodes.VOLATILITY_NORMAL));
        }
        if (value.compareTo(horizonPolicy.highUpperBoundBps()) <= 0) {
            return VolatilityAssessment.available(VolatilityLevel.HIGH,
                    concat(inherited, VolatilityReasonCodes.VOLATILITY_HIGH));
        }
        return VolatilityAssessment.available(VolatilityLevel.EXTREME,
                concat(inherited, VolatilityReasonCodes.VOLATILITY_EXTREME));
    }

    /**
     * The <b>single canonical</b> horizon → realized-volatility selection of the volatility dimension
     * ({@code 1S → realizedVolatilityBps1s, 5S → realizedVolatilityBps5s, 15S → realizedVolatilityBps15s,
     * 60S → realizedVolatilityBps60s}); {@code null} when that value is absent. The deprecated
     * upstream alias is never read. Package-visible for tests.
     */
    static BigDecimal realizedVolatilityBpsOf(RegimeFeature regime, MarketHorizon horizon) {
        requireNonNull(regime, "regime");
        requireNonNull(horizon, "horizon");
        return switch (horizon) {
            case H1S -> regime.realizedVolatilityBps1s();
            case H5S -> regime.realizedVolatilityBps5s();
            case H15S -> regime.realizedVolatilityBps15s();
            case H60S -> regime.realizedVolatilityBps60s();
        };
    }

    private static List<ReasonCode> concat(List<ReasonCode> inherited, ReasonCode code) {
        Set<ReasonCode> merged = new LinkedHashSet<>(inherited);
        merged.add(code);
        return List.copyOf(merged);
    }
}
