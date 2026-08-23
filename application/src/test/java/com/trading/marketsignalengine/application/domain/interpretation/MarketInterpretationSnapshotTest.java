package com.trading.marketsignalengine.application.domain.interpretation;

import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.EVALUATED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.NEUTRAL_MARKET;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.QUALITY_BLOCKED;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.VALID_UNTIL;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.blockedSnapshotBuilder;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.candidateLong5s;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.eligibleBullish;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.eligibleNeutral;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.insufficientData;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.shortEligibleLongWarmingUp;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.strength;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.validSnapshotBuilder;
import static com.trading.marketsignalengine.application.domain.interpretation.InterpretationFixtures.warmingUp;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketInterpretationSnapshotTest {

    // ------------------------------------------------------------------ horizons

    @Test
    void exactlyOneAssessmentPerHorizonStoredInCanonicalOrder() {
        MarketInterpretationSnapshot snapshot = validSnapshotBuilder().build();
        assertEquals(MarketHorizon.canonicalOrder(),
                snapshot.horizonAssessments().stream().map(HorizonAssessment::horizon).toList());
        assertSame(snapshot.horizonAssessments().get(2), snapshot.horizon(H15S));
    }

    @Test
    void missingHorizonIsRejected() {
        List<HorizonAssessment> three = List.of(eligibleBullish(H1S), eligibleBullish(H5S), eligibleBullish(H15S));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> validSnapshotBuilder().horizonAssessments(three).build());
        assertTrue(e.getMessage().contains("H60S"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().horizonAssessments(List.of()).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().horizonAssessments(null).build());
    }

    @Test
    void duplicateHorizonIsRejected() {
        List<HorizonAssessment> duplicate = List.of(eligibleBullish(H1S), eligibleBullish(H5S), eligibleBullish(H5S),
                eligibleBullish(H15S), eligibleBullish(H60S));
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().horizonAssessments(duplicate).build());
        List<HorizonAssessment> duplicateInsteadOfMissing = List.of(eligibleBullish(H1S), eligibleBullish(H5S),
                eligibleBullish(H5S), eligibleBullish(H60S));
        assertThrows(IllegalArgumentException.class,
                () -> validSnapshotBuilder().horizonAssessments(duplicateInsteadOfMissing).build());
    }

    @Test
    void unorderedAssessmentsAreNormalisedToCanonicalOrder() {
        // documented policy: any input order is accepted, the stored order is always 1S, 5S, 15S, 60S
        List<HorizonAssessment> reversed = new ArrayList<>(InterpretationFixtures.allEligibleBullish());
        java.util.Collections.reverse(reversed);
        MarketInterpretationSnapshot snapshot = validSnapshotBuilder().horizonAssessments(reversed).build();
        assertEquals(List.of(H1S, H5S, H15S, H60S),
                snapshot.horizonAssessments().stream().map(HorizonAssessment::horizon).toList());
        assertEquals(validSnapshotBuilder().build(), snapshot);
    }

    // ------------------------------------------------------------------ cross-horizon ↔ horizons

    @Test
    void crossHorizonMayOnlyReferenceEligibleHorizons() {
        // 15S / 60S warming up: a cross assessment over 1S / 5S is fine
        MarketInterpretationSnapshot partial = validSnapshotBuilder()
                .interpretationQuality(InterpretationQuality.degraded(true, List.of(ReasonCode.of("HORIZONS_PARTIALLY_ELIGIBLE"))))
                .horizonAssessments(shortEligibleLongWarmingUp())
                .crossHorizonAssessment(CrossHorizonAssessment.alignedBullish(strength("0.5"), H5S, List.of(H1S, H5S), null, List.of()))
                .build();
        assertEquals(List.of(H1S, H5S), partial.crossHorizonAssessment().participatingHorizons());

        // participating non-eligible
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder()
                .interpretationQuality(InterpretationQuality.degraded(true, List.of()))
                .horizonAssessments(shortEligibleLongWarmingUp())
                .crossHorizonAssessment(CrossHorizonAssessment.alignedBullish(null, null, List.of(H1S, H5S, H60S), null, List.of()))
                .build());
        // dominant non-eligible
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder()
                .interpretationQuality(InterpretationQuality.degraded(true, List.of()))
                .horizonAssessments(shortEligibleLongWarmingUp())
                .crossHorizonAssessment(CrossHorizonAssessment.alignedBullish(null, H60S, List.of(H1S, H5S, H60S), null, List.of()))
                .build());
        // conflicting non-eligible (15S warming up but listed as conflicting)
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder()
                .interpretationQuality(InterpretationQuality.degraded(true, List.of()))
                .horizonAssessments(shortEligibleLongWarmingUp())
                .crossHorizonAssessment(CrossHorizonAssessment.conflicting(null, H5S, List.of(H1S, H5S, H15S), List.of(H15S), null, List.of()))
                .marketOpportunity(MarketOpportunity.noOpportunity(List.of(ReasonCode.of("CROSS_HORIZON_CONFLICT"))))
                .build());
        // insufficient data listing a non-eligible participant
        assertThrows(IllegalArgumentException.class, () -> blockedSnapshotBuilder()
                .crossHorizonAssessment(insufficientData(List.of(H1S)))
                .build());
    }

    @Test
    void candidateSetupHorizonMustBeEligible() {
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder()
                .interpretationQuality(InterpretationQuality.degraded(true, List.of()))
                .horizonAssessments(shortEligibleLongWarmingUp())
                .crossHorizonAssessment(CrossHorizonAssessment.alignedBullish(null, H5S, List.of(H1S, H5S), null, List.of()))
                .marketOpportunity(MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION, OpportunitySide.LONG,
                        H60S, null, List.of(), List.of()))
                .build());
    }

    // ------------------------------------------------------------------ quality ↔ opportunity

    @Test
    void qualityOpportunityConsistencyMatrix() {
        MarketOpportunity candidate = candidateLong5s();
        MarketOpportunity none = MarketOpportunity.noOpportunity(List.of(NEUTRAL_MARKET));
        MarketOpportunity blocked = MarketOpportunity.blocked(List.of(QUALITY_BLOCKED));
        MarketOpportunity unknown = MarketOpportunity.unknown(List.of());

        // eligibleForTrading = true (OK, DEGRADED-by-policy): CANDIDATE / NO_OPPORTUNITY / UNKNOWN; never BLOCKED
        for (InterpretationQuality eligible : List.of(InterpretationQuality.ok(List.of()), InterpretationQuality.degraded(true, List.of()))) {
            assertNotNull(validSnapshotBuilder().interpretationQuality(eligible).marketOpportunity(candidate).build());
            assertNotNull(validSnapshotBuilder().interpretationQuality(eligible).marketOpportunity(none).build());
            assertNotNull(validSnapshotBuilder().interpretationQuality(eligible).marketOpportunity(unknown).build());
            assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder()
                    .interpretationQuality(eligible).marketOpportunity(blocked).build(), eligible.status() + " + BLOCKED");
        }

        // eligibleForTrading = false (DEGRADED-by-policy, BLOCKED, NO_DATA, UNKNOWN): only BLOCKED
        for (InterpretationQuality notEligible : List.of(
                InterpretationQuality.degraded(false, List.of()),
                InterpretationQuality.blocked(List.of()),
                InterpretationQuality.noData(List.of()),
                InterpretationQuality.unknown(List.of()))) {
            assertNotNull(validSnapshotBuilder().interpretationQuality(notEligible).marketOpportunity(blocked).build(),
                    notEligible.status() + " + BLOCKED");
            for (MarketOpportunity forbidden : List.of(candidate, none, unknown)) {
                assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder()
                        .interpretationQuality(notEligible).marketOpportunity(forbidden).build(),
                        notEligible.status() + " + " + forbidden.status());
            }
        }
        // quality OK always eligible (enforced in InterpretationQuality, re-checked here through the aggregate)
        assertTrue(validSnapshotBuilder().build().isEligibleForTrading());
        assertEquals(OpportunityStatus.BLOCKED, blockedSnapshotBuilder().build().marketOpportunity().status());
    }

    // ------------------------------------------------------------------ timing / identity

    @Test
    void validUntilMustBeStrictlyAfterEvaluatedAt() {
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().validUntil(EVALUATED_AT).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().validUntil(EVALUATED_AT.minusMillis(1)).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().validUntil(null).build());
        assertEquals(EVALUATED_AT.plusMillis(1), validSnapshotBuilder().validUntil(EVALUATED_AT.plusMillis(1)).build().validUntil());
    }

    @Test
    void evaluatedAtMustEqualSourceEvaluationAt() {
        assertThrows(IllegalArgumentException.class,
                () -> validSnapshotBuilder().evaluatedAt(EVALUATED_AT.plusMillis(1)).validUntil(VALID_UNTIL).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().evaluatedAt(null).build());
        assertThrows(IllegalArgumentException.class,
                () -> validSnapshotBuilder().evaluatedAt(Instant.EPOCH).validUntil(Instant.EPOCH.plusSeconds(1)).build());
        // moving both together is fine
        FeatureLineage shifted = new FeatureLineage("feat-0001", 1, "mfs-features-v2", "cfg-test-mfs-v2",
                EVALUATED_AT.plusSeconds(1), EVALUATED_AT.plusSeconds(1), "TRADE");
        assertEquals(EVALUATED_AT.plusSeconds(1), validSnapshotBuilder().featureLineage(shifted)
                .evaluatedAt(EVALUATED_AT.plusSeconds(1)).validUntil(EVALUATED_AT.plusSeconds(6)).build().evaluatedAt());
    }

    @Test
    void identityFieldsAndComponentsAreMandatory() {
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().exchange(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().marketType(null).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().base("").build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().quote("").build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().symbol(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().instrumentId(null).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().interpretationQuality(null).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().crossHorizonAssessment(null).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().marketOpportunity(null).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().featureLineage(null).build());
        assertThrows(IllegalArgumentException.class, () -> validSnapshotBuilder().interpretationLineage(null).build());
    }

    @Test
    void snapshotIdIsDerivedFromLineageAndCannotBeInjected() {
        MarketInterpretationSnapshot snapshot = validSnapshotBuilder().build();
        assertEquals(InterpretationSnapshotIdGenerator.generate(snapshot.featureLineage(), snapshot.interpretationLineage()),
                snapshot.interpretationSnapshotId());
        // the canonical constructor re-derives the id: an arbitrary id is rejected
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationSnapshot("arbitrary-id",
                snapshot.exchange(), snapshot.marketType(), snapshot.base(), snapshot.quote(), snapshot.symbol(),
                snapshot.instrumentId(), snapshot.evaluatedAt(), snapshot.validUntil(), snapshot.interpretationQuality(),
                snapshot.horizonAssessments(), snapshot.crossHorizonAssessment(), snapshot.marketOpportunity(),
                snapshot.featureLineage(), snapshot.interpretationLineage()));
        // the same lineage with the correct id is accepted
        assertEquals(snapshot, new MarketInterpretationSnapshot(snapshot.interpretationSnapshotId(),
                snapshot.exchange(), snapshot.marketType(), snapshot.base(), snapshot.quote(), snapshot.symbol(),
                snapshot.instrumentId(), snapshot.evaluatedAt(), snapshot.validUntil(), snapshot.interpretationQuality(),
                snapshot.horizonAssessments(), snapshot.crossHorizonAssessment(), snapshot.marketOpportunity(),
                snapshot.featureLineage(), snapshot.interpretationLineage()));
        // validUntil and market content do not influence the id
        assertEquals(snapshot.interpretationSnapshotId(), validSnapshotBuilder()
                .validUntil(VALID_UNTIL.plusSeconds(30))
                .horizonAssessments(List.of(eligibleNeutral(H1S), eligibleNeutral(H5S), eligibleNeutral(H15S), eligibleNeutral(H60S)))
                .crossHorizonAssessment(CrossHorizonAssessment.neutral(null, MarketHorizon.canonicalOrder(), null, List.of()))
                .marketOpportunity(MarketOpportunity.noOpportunity(List.of(NEUTRAL_MARKET)))
                .build().interpretationSnapshotId());
    }

    // ------------------------------------------------------------------ immutability

    @Test
    void aggregateCollectionsAreImmutable() {
        MarketInterpretationSnapshot snapshot = validSnapshotBuilder().build();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.horizonAssessments().add(warmingUp(H1S)));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.horizonAssessments().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.horizon(H1S).evidenceAssessments().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.horizon(H1S).reasonCodes().add(NEUTRAL_MARKET));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.horizon(H1S).eligibility().reasonCodes().add(NEUTRAL_MARKET));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.crossHorizonAssessment().participatingHorizons().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.interpretationQuality().reasonCodes().add(NEUTRAL_MARKET));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.marketOpportunity().invalidationCodes().clear());

        // a mutable input list handed to the builder does not leak into the aggregate
        List<HorizonAssessment> mutable = new ArrayList<>(InterpretationFixtures.allEligibleBullish());
        MarketInterpretationSnapshot built = validSnapshotBuilder().horizonAssessments(mutable).build();
        mutable.clear();
        assertEquals(4, built.horizonAssessments().size());
    }
}
