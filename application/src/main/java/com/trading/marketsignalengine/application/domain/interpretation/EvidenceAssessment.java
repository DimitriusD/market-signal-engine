package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import java.util.List;

/**
 * Assessment of one evidence dimension within one horizon (contract: {@code EvidenceAssessmentEvent}).
 * Invariants:
 * <ul>
 *   <li>{@code dimension}, {@code availabilityStatus}, {@code direction} non-null;</li>
 *   <li>{@code availabilityStatus != AVAILABLE} ⇒ {@code direction = UNKNOWN} and
 *       {@code evidenceStrength} absent — unavailable evidence has no reading, and UNKNOWN is never
 *       read as NEUTRAL;</li>
 *   <li>{@code evidenceStrength}, when present, is a valid {@link EvidenceStrength} (in [0, 1]); absence
 *       is {@code null}, never zero.</li>
 * </ul>
 * AVAILABLE evidence may still read UNKNOWN (computed but not interpretable) — the model does not force
 * a direction on it.
 */
public record EvidenceAssessment(
        EvidenceDimension dimension,
        EvidenceAvailabilityStatus availabilityStatus,
        InterpretationDirection direction,
        EvidenceStrength evidenceStrength,
        List<ReasonCode> reasonCodes) {

    public EvidenceAssessment {
        requireNonNull(dimension, "evidence.dimension");
        requireNonNull(availabilityStatus, "evidence.availabilityStatus");
        requireNonNull(direction, "evidence.direction");
        reasonCodes = Invariants.reasonCodes(reasonCodes, "evidence.reasonCodes");
        if (!availabilityStatus.isAvailable()) {
            require(direction == InterpretationDirection.UNKNOWN,
                    dimension + " evidence is " + availabilityStatus + " and must have direction UNKNOWN, got " + direction);
            require(evidenceStrength == null,
                    dimension + " evidence is " + availabilityStatus + " and must not carry an evidence strength");
        }
    }

    public boolean isAvailable() {
        return availabilityStatus.isAvailable();
    }

    /** Computed, usable evidence with a directional reading and an optional strength. */
    public static EvidenceAssessment available(EvidenceDimension dimension,
                                               InterpretationDirection direction,
                                               EvidenceStrength evidenceStrength,
                                               List<ReasonCode> reasonCodes) {
        return new EvidenceAssessment(dimension, EvidenceAvailabilityStatus.AVAILABLE, direction, evidenceStrength, reasonCodes);
    }

    /**
     * Evidence that could not be used; {@code status} must not be AVAILABLE. Direction is UNKNOWN and
     * strength absent by construction.
     */
    public static EvidenceAssessment notAvailable(EvidenceDimension dimension,
                                                  EvidenceAvailabilityStatus status,
                                                  List<ReasonCode> reasonCodes) {
        requireNonNull(status, "evidence.availabilityStatus");
        require(!status.isAvailable(), "notAvailable(...) requires a non-AVAILABLE status");
        return new EvidenceAssessment(dimension, status, InterpretationDirection.UNKNOWN, null, reasonCodes);
    }

    public static EvidenceAssessment unavailable(EvidenceDimension dimension, List<ReasonCode> reasonCodes) {
        return notAvailable(dimension, EvidenceAvailabilityStatus.UNAVAILABLE, reasonCodes);
    }

    public static EvidenceAssessment untrusted(EvidenceDimension dimension, List<ReasonCode> reasonCodes) {
        return notAvailable(dimension, EvidenceAvailabilityStatus.UNTRUSTED, reasonCodes);
    }

    public static EvidenceAssessment failed(EvidenceDimension dimension, List<ReasonCode> reasonCodes) {
        return notAvailable(dimension, EvidenceAvailabilityStatus.FAILED, reasonCodes);
    }
}
