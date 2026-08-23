package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonBlank;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requirePositiveInstant;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The V2 aggregate: one complete, immutable market interpretation of one feature snapshot (contract:
 * {@code MarketInterpretationSnapshotEvent}, minus transport constants — {@code schemaVersion = 2},
 * {@code eventType}, {@code sourceStream} belong to the Avro mapper, not to the domain).
 *
 * <p>Build it through {@link #builder()}: the builder derives {@link #interpretationSnapshotId()} from
 * the lineage with {@link InterpretationSnapshotIdGenerator}, so no caller can inject an arbitrary id.
 * The canonical constructor is public (it is a record) but re-derives and verifies the id, so an
 * aggregate with an inconsistent id cannot exist either way.
 *
 * <h2>Enforced invariants</h2>
 * <ul>
 *   <li>identity: {@code exchange}, {@code marketType}, {@code base}, {@code quote}, {@code symbol},
 *       {@code instrumentId} non-blank (they become event metadata);</li>
 *   <li>timing: {@code evaluatedAt} positive; {@code validUntil} strictly after {@code evaluatedAt};
 *       {@code evaluatedAt == featureLineage.sourceEvaluationAt} (the interpretation is evaluated
 *       as-of the source feature evaluation tick — deterministic across replay);</li>
 *   <li>horizons: exactly one {@link HorizonAssessment} per {@link MarketHorizon} (1S, 5S, 15S, 60S) —
 *       missing or duplicate horizons are rejected; accepted in any order, <b>stored in canonical
 *       order</b> (safe: uniqueness is enforced first);</li>
 *   <li>cross-horizon ↔ horizons: every participating / conflicting / dominant horizon is ELIGIBLE in
 *       {@code horizonAssessments}; a non-eligible horizon can be none of them;</li>
 *   <li>opportunity ↔ horizons: a CANDIDATE's {@code setupHorizon} is an ELIGIBLE horizon;</li>
 *   <li>quality ↔ opportunity: {@code eligibleForTrading = false} ⇔ opportunity BLOCKED (so quality
 *       BLOCKED / NO_DATA / UNKNOWN ⇒ opportunity BLOCKED, and a BLOCKED opportunity never appears on a
 *       snapshot that is eligible for trading — a permitted search that found nothing is NO_OPPORTUNITY);
 *       opportunity CANDIDATE, NO_OPPORTUNITY or UNKNOWN ⇒ {@code eligibleForTrading = true}; quality OK ⇒
 *       {@code eligibleForTrading = true} (guaranteed by {@link InterpretationQuality});</li>
 *   <li>id: {@code interpretationSnapshotId == InterpretationSnapshotIdGenerator.generate(lineage)}.</li>
 * </ul>
 */
public record MarketInterpretationSnapshot(
        String interpretationSnapshotId,
        String exchange,
        String marketType,
        String base,
        String quote,
        String symbol,
        String instrumentId,
        Instant evaluatedAt,
        Instant validUntil,
        InterpretationQuality interpretationQuality,
        List<HorizonAssessment> horizonAssessments,
        CrossHorizonAssessment crossHorizonAssessment,
        MarketOpportunity marketOpportunity,
        FeatureLineage featureLineage,
        InterpretationLineage interpretationLineage) {

    public MarketInterpretationSnapshot {
        requireNonBlank(exchange, "exchange");
        requireNonBlank(marketType, "marketType");
        requireNonBlank(base, "base");
        requireNonBlank(quote, "quote");
        requireNonBlank(symbol, "symbol");
        requireNonBlank(instrumentId, "instrumentId");
        requirePositiveInstant(evaluatedAt, "evaluatedAt");
        requireNonNull(validUntil, "validUntil");
        requireNonNull(interpretationQuality, "interpretationQuality");
        requireNonNull(crossHorizonAssessment, "crossHorizonAssessment");
        requireNonNull(marketOpportunity, "marketOpportunity");
        requireNonNull(featureLineage, "featureLineage");
        requireNonNull(interpretationLineage, "interpretationLineage");

        require(validUntil.isAfter(evaluatedAt),
                "validUntil " + validUntil + " must be strictly after evaluatedAt " + evaluatedAt);
        require(evaluatedAt.equals(featureLineage.sourceEvaluationAt()),
                "evaluatedAt " + evaluatedAt + " must equal featureLineage.sourceEvaluationAt "
                        + featureLineage.sourceEvaluationAt());

        horizonAssessments = canonicalAssessments(horizonAssessments);
        validateCrossHorizonReferences(horizonAssessments, crossHorizonAssessment);
        validateOpportunity(interpretationQuality, marketOpportunity, horizonAssessments);

        String expectedId = InterpretationSnapshotIdGenerator.generate(featureLineage, interpretationLineage);
        requireNonBlank(interpretationSnapshotId, "interpretationSnapshotId");
        require(expectedId.equals(interpretationSnapshotId),
                "interpretationSnapshotId " + interpretationSnapshotId + " is not the deterministic id " + expectedId
                        + " derived from the lineage (use MarketInterpretationSnapshot.builder())");
    }

    public static Builder builder() {
        return new Builder();
    }

    public HorizonAssessment horizon(MarketHorizon horizon) {
        requireNonNull(horizon, "horizon");
        return horizonAssessments.get(horizon.ordinal());
    }

    public boolean isEligibleForTrading() {
        return interpretationQuality.eligibleForTrading();
    }

    // ------------------------------------------------------------------ validation

    private static List<HorizonAssessment> canonicalAssessments(List<HorizonAssessment> assessments) {
        requireNonNull(assessments, "horizonAssessments");
        Map<MarketHorizon, HorizonAssessment> byHorizon = new EnumMap<>(MarketHorizon.class);
        for (HorizonAssessment assessment : assessments) {
            if (assessment == null) {
                throw new IllegalArgumentException("horizonAssessments must not contain null");
            }
            if (byHorizon.putIfAbsent(assessment.horizon(), assessment) != null) {
                throw new IllegalArgumentException("horizonAssessments contains duplicate horizon " + assessment.horizon());
            }
        }
        List<HorizonAssessment> ordered = new ArrayList<>(MarketHorizon.canonicalOrder().size());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            HorizonAssessment assessment = byHorizon.get(horizon);
            if (assessment == null) {
                throw new IllegalArgumentException("horizonAssessments is missing horizon " + horizon
                        + " (exactly one assessment per " + MarketHorizon.canonicalOrder() + " is required)");
            }
            ordered.add(assessment);
        }
        return List.copyOf(ordered);
    }

    private static void validateCrossHorizonReferences(List<HorizonAssessment> assessments,
                                                       CrossHorizonAssessment cross) {
        for (MarketHorizon horizon : cross.participatingHorizons()) {
            requireEligible(assessments, horizon, "participating");
        }
        for (MarketHorizon horizon : cross.conflictingHorizons()) {
            requireEligible(assessments, horizon, "conflicting");
        }
        if (cross.dominantHorizon() != null) {
            requireEligible(assessments, cross.dominantHorizon(), "dominant");
        }
    }

    private static void validateOpportunity(InterpretationQuality quality, MarketOpportunity opportunity,
                                            List<HorizonAssessment> assessments) {
        if (!quality.eligibleForTrading()) {
            require(opportunity.status() == OpportunityStatus.BLOCKED,
                    "quality " + quality.status() + " with eligibleForTrading=false requires opportunity BLOCKED, got "
                            + opportunity.status());
        }
        if (opportunity.status().requiresEligibleForTrading()) {
            require(quality.eligibleForTrading(),
                    "opportunity " + opportunity.status() + " requires quality.eligibleForTrading = true (quality is "
                            + quality.status() + ")");
        }
        if (opportunity.status() == OpportunityStatus.BLOCKED) {
            require(!quality.eligibleForTrading(),
                    "opportunity BLOCKED marks a snapshot the engine was not allowed to use, so quality.eligibleForTrading "
                            + "must be false (quality is " + quality.status() + "); a permitted search that found nothing is NO_OPPORTUNITY");
        }
        if (opportunity.setupHorizon() != null) {
            requireEligible(assessments, opportunity.setupHorizon(), "opportunity setup");
        }
    }

    private static void requireEligible(List<HorizonAssessment> assessments, MarketHorizon horizon, String role) {
        HorizonAssessment assessment = assessments.get(horizon.ordinal());
        // canonicalAssessments guarantees index == ordinal and presence of every horizon
        require(assessment.isEligible(),
                role + " horizon " + horizon + " is " + assessment.eligibilityStatus() + ", only ELIGIBLE horizons may be " + role);
    }

    // ------------------------------------------------------------------ builder / assembler

    /**
     * Assembles the aggregate and derives the deterministic {@code interpretationSnapshotId} from the
     * lineage. Every invariant is enforced by the record constructor on {@link #build()}.
     */
    public static final class Builder {
        private String exchange;
        private String marketType;
        private String base;
        private String quote;
        private String symbol;
        private String instrumentId;
        private Instant evaluatedAt;
        private Instant validUntil;
        private InterpretationQuality interpretationQuality;
        private List<HorizonAssessment> horizonAssessments = List.of();
        private CrossHorizonAssessment crossHorizonAssessment;
        private MarketOpportunity marketOpportunity;
        private FeatureLineage featureLineage;
        private InterpretationLineage interpretationLineage;

        private Builder() {
        }

        public Builder exchange(String exchange) {
            this.exchange = exchange;
            return this;
        }

        public Builder marketType(String marketType) {
            this.marketType = marketType;
            return this;
        }

        public Builder base(String base) {
            this.base = base;
            return this;
        }

        public Builder quote(String quote) {
            this.quote = quote;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder instrumentId(String instrumentId) {
            this.instrumentId = instrumentId;
            return this;
        }

        public Builder evaluatedAt(Instant evaluatedAt) {
            this.evaluatedAt = evaluatedAt;
            return this;
        }

        public Builder validUntil(Instant validUntil) {
            this.validUntil = validUntil;
            return this;
        }

        public Builder interpretationQuality(InterpretationQuality interpretationQuality) {
            this.interpretationQuality = interpretationQuality;
            return this;
        }

        public Builder horizonAssessments(List<HorizonAssessment> horizonAssessments) {
            this.horizonAssessments = horizonAssessments == null ? null : List.copyOf(horizonAssessments);
            return this;
        }

        public Builder crossHorizonAssessment(CrossHorizonAssessment crossHorizonAssessment) {
            this.crossHorizonAssessment = crossHorizonAssessment;
            return this;
        }

        public Builder marketOpportunity(MarketOpportunity marketOpportunity) {
            this.marketOpportunity = marketOpportunity;
            return this;
        }

        public Builder featureLineage(FeatureLineage featureLineage) {
            this.featureLineage = featureLineage;
            return this;
        }

        public Builder interpretationLineage(InterpretationLineage interpretationLineage) {
            this.interpretationLineage = interpretationLineage;
            return this;
        }

        public MarketInterpretationSnapshot build() {
            requireNonNull(featureLineage, "featureLineage");
            requireNonNull(interpretationLineage, "interpretationLineage");
            String id = InterpretationSnapshotIdGenerator.generate(featureLineage, interpretationLineage);
            return new MarketInterpretationSnapshot(id, exchange, marketType, base, quote, symbol, instrumentId,
                    evaluatedAt, validUntil, interpretationQuality, horizonAssessments, crossHorizonAssessment,
                    marketOpportunity, featureLineage, interpretationLineage);
        }
    }
}
