package com.trading.marketsignalengine.application.domain.interpretation.quality;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQuality;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQualityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The internal result of the quality layer for one validated feature snapshot: the upstream
 * aggregate status, the engine's overall {@link InterpretationQuality} (Stage 2 model), the
 * {@link TimingAssessment}, exactly four {@link HorizonEligibility} verdicts, the typed failed feature
 * groups and the upstream future-event flag. Internal model, not an Avro DTO; immutable.
 *
 * <p>The overall reason codes have exactly one source of truth: {@link InterpretationQuality#reasonCodes()}.
 * {@link #reasonCodes()} is a convenience view of that list (same instance, immutable, producer order),
 * so an Avro mapper and internal explainability can never read two different explanations.
 *
 * <p>Structural invariants (the policy itself lives in {@link QualityAssessmentResolver}):
 * {@code eligibleForTrading = true} requires at least one ELIGIBLE horizon; status {@code OK} requires
 * every horizon ELIGIBLE, FRESH timing, no failed feature groups and no future event; a non-FRESH timing
 * or {@code UNSAFE} / {@code NO_DATA} source can never be eligible for trading. "Eligible for trading"
 * here means only that interpretation may continue — no opportunity is implied.
 */
public record QualityAssessment(
        FeatureQualityStatus sourceQualityStatus,
        InterpretationQuality interpretationQuality,
        TimingAssessment timing,
        HorizonEligibilities horizonEligibilities,
        List<FeatureGroupId> failedFeatureGroups,
        boolean futureEventDetected) {

    public QualityAssessment {
        requireNonNull(sourceQualityStatus, "sourceQualityStatus");
        requireNonNull(interpretationQuality, "interpretationQuality");
        requireNonNull(timing, "timing");
        requireNonNull(horizonEligibilities, "horizonEligibilities");
        failedFeatureGroups = distinctGroups(failedFeatureGroups);

        boolean eligible = interpretationQuality.eligibleForTrading();
        require(!eligible || horizonEligibilities.anyEligible(),
                "eligibleForTrading = true requires at least one ELIGIBLE horizon");
        require(!eligible || timing.isFresh(),
                "eligibleForTrading = true requires FRESH timing, got " + timing.status());
        require(!eligible || (sourceQualityStatus != FeatureQualityStatus.UNSAFE
                        && sourceQualityStatus != FeatureQualityStatus.NO_DATA),
                "eligibleForTrading = true is impossible for source quality " + sourceQualityStatus);
        if (interpretationQuality.status() == InterpretationQualityStatus.OK) {
            require(horizonEligibilities.allEligible(), "quality OK requires every horizon ELIGIBLE");
            require(failedFeatureGroups.isEmpty(), "quality OK requires no failed feature groups");
            require(!futureEventDetected, "quality OK requires futureEventDetected = false");
        }
    }

    public InterpretationQualityStatus status() {
        return interpretationQuality.status();
    }

    /** Whether interpretation may continue (not: whether an opportunity exists). */
    public boolean eligibleForTrading() {
        return interpretationQuality.eligibleForTrading();
    }

    /**
     * The overall reason codes — the single authoritative list, read through from
     * {@link InterpretationQuality#reasonCodes()} (immutable, deterministic producer order).
     */
    public List<ReasonCode> reasonCodes() {
        return interpretationQuality.reasonCodes();
    }

    public HorizonEligibility eligibilityOf(MarketHorizon horizon) {
        return horizonEligibilities.of(horizon);
    }

    public List<MarketHorizon> eligibleHorizons() {
        return horizonEligibilities.eligibleHorizons();
    }

    public boolean hasFailedFeatureGroup(FeatureGroupId group) {
        return failedFeatureGroups.contains(requireNonNull(group, "group"));
    }

    /** Immutable, null-free; a duplicate group is a producer bug here (the resolver collapses wire duplicates). */
    private static List<FeatureGroupId> distinctGroups(List<FeatureGroupId> groups) {
        if (groups == null) {
            return List.of();
        }
        Set<FeatureGroupId> seen = new LinkedHashSet<>();
        for (FeatureGroupId group : groups) {
            requireNonNull(group, "failedFeatureGroups element");
            require(seen.add(group), "failedFeatureGroups contains duplicate group " + group);
        }
        return List.copyOf(seen);
    }
}
