package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Independent interpretation of the market over one horizon (contract: {@code HorizonAssessmentEvent}).
 * Invariants:
 * <ul>
 *   <li>{@code horizon}, {@code eligibility}, {@code direction} non-null;</li>
 *   <li>evidence dimensions are unique within the horizon; the list is stored in canonical
 *       {@link EvidenceDimension} order (accepted in any order — safe because uniqueness is enforced);</li>
 *   <li>{@code eligibility != ELIGIBLE} ⇒ {@code direction = UNKNOWN}, {@code evidenceStrength} absent,
 *       {@code regime} absent, and no nested evidence is AVAILABLE (a horizon that may not be read has
 *       no usable evidence). Such a horizon is UNKNOWN, never NEUTRAL;</li>
 *   <li>{@code evidenceStrength}, when present, is a valid {@link EvidenceStrength}.</li>
 * </ul>
 * An ELIGIBLE horizon may read UNKNOWN (eligible but not interpretable) — direction is not forced.
 */
public record HorizonAssessment(
        MarketHorizon horizon,
        HorizonEligibility eligibility,
        InterpretationDirection direction,
        EvidenceStrength evidenceStrength,
        MarketRegime regime,
        List<EvidenceAssessment> evidenceAssessments,
        List<ReasonCode> reasonCodes) {

    public HorizonAssessment {
        requireNonNull(horizon, "horizon");
        requireNonNull(eligibility, "eligibility");
        requireNonNull(direction, "direction");
        evidenceAssessments = canonicalEvidence(evidenceAssessments, horizon);
        reasonCodes = Invariants.reasonCodes(reasonCodes, horizon.wireValue() + ".reasonCodes");
        if (!eligibility.isEligible()) {
            String prefix = horizon.wireValue() + " horizon is " + eligibility.status();
            require(direction == InterpretationDirection.UNKNOWN, prefix + " and must have direction UNKNOWN, got " + direction);
            require(evidenceStrength == null, prefix + " and must not carry an evidence strength");
            require(regime == null, prefix + " and must not carry a regime");
            for (EvidenceAssessment evidence : evidenceAssessments) {
                require(!evidence.isAvailable(),
                        prefix + " and must not carry AVAILABLE " + evidence.dimension() + " evidence");
            }
        }
    }

    public boolean isEligible() {
        return eligibility.isEligible();
    }

    public HorizonEligibilityStatus eligibilityStatus() {
        return eligibility.status();
    }

    public Optional<EvidenceAssessment> evidence(EvidenceDimension dimension) {
        requireNonNull(dimension, "dimension");
        for (EvidenceAssessment evidence : evidenceAssessments) {
            if (evidence.dimension() == dimension) {
                return Optional.of(evidence);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ factories

    /** An ELIGIBLE horizon with its interpreted direction, optional strength / regime and evidence. */
    public static HorizonAssessment eligible(MarketHorizon horizon,
                                             InterpretationDirection direction,
                                             EvidenceStrength evidenceStrength,
                                             MarketRegime regime,
                                             List<EvidenceAssessment> evidenceAssessments,
                                             List<ReasonCode> reasonCodes) {
        return new HorizonAssessment(horizon, HorizonEligibility.eligible(), direction, evidenceStrength, regime,
                evidenceAssessments, reasonCodes);
    }

    /**
     * A horizon that may not be interpreted ({@code eligibility.status != ELIGIBLE}): direction UNKNOWN,
     * no strength, no regime. Non-AVAILABLE evidence may be attached to explain which dimension was
     * missing.
     */
    public static HorizonAssessment notEligible(MarketHorizon horizon,
                                                HorizonEligibility eligibility,
                                                List<EvidenceAssessment> evidenceAssessments,
                                                List<ReasonCode> reasonCodes) {
        requireNonNull(eligibility, "eligibility");
        require(!eligibility.isEligible(), "notEligible(...) requires a non-ELIGIBLE eligibility");
        return new HorizonAssessment(horizon, eligibility, InterpretationDirection.UNKNOWN, null, null,
                evidenceAssessments, reasonCodes);
    }

    public static HorizonAssessment warmingUp(MarketHorizon horizon, List<ReasonCode> reasonCodes) {
        return notEligible(horizon, HorizonEligibility.warmingUp(reasonCodes), List.of(), List.of());
    }

    public static HorizonAssessment unavailable(MarketHorizon horizon, List<ReasonCode> reasonCodes) {
        return notEligible(horizon, HorizonEligibility.unavailable(reasonCodes), List.of(), List.of());
    }

    public static HorizonAssessment untrusted(MarketHorizon horizon, List<ReasonCode> reasonCodes) {
        return notEligible(horizon, HorizonEligibility.untrusted(reasonCodes), List.of(), List.of());
    }

    public static HorizonAssessment failed(MarketHorizon horizon, List<ReasonCode> reasonCodes) {
        return notEligible(horizon, HorizonEligibility.failed(reasonCodes), List.of(), List.of());
    }

    public static HorizonAssessment unknown(MarketHorizon horizon, List<ReasonCode> reasonCodes) {
        return notEligible(horizon, HorizonEligibility.unknown(reasonCodes), List.of(), List.of());
    }

    // ------------------------------------------------------------------ helpers

    private static List<EvidenceAssessment> canonicalEvidence(List<EvidenceAssessment> evidence, MarketHorizon horizon) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        Map<EvidenceDimension, EvidenceAssessment> byDimension = new EnumMap<>(EvidenceDimension.class);
        for (EvidenceAssessment assessment : evidence) {
            if (assessment == null) {
                throw new IllegalArgumentException(horizon.wireValue() + ".evidenceAssessments must not contain null");
            }
            if (byDimension.putIfAbsent(assessment.dimension(), assessment) != null) {
                throw new IllegalArgumentException(
                        horizon.wireValue() + ".evidenceAssessments contains duplicate dimension " + assessment.dimension());
            }
        }
        return List.copyOf(new ArrayList<>(byDimension.values()));
    }
}
