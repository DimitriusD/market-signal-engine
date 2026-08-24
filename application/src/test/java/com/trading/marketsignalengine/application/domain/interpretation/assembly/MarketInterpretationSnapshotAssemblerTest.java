package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.ALLOW_VOLATILE_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.EVENT_TIME;
import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.OPPORTUNITY_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.quality;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.FeatureLineage;
import com.trading.marketsignalengine.application.domain.interpretation.FeatureLineageFactory;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQualityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationSnapshotIdGenerator;
import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunitySide;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityType;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.MarketOpportunityEvaluation;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Stage 9 safe public boundary end to end in the domain: one entry point from a snapshot + real
 * Stage 3 quality assessment + aggregate policy to a complete {@link MarketInterpretationSnapshot},
 * verbatim mapping of identity / quality / horizons / cross / lineage, deterministic validity and
 * id, expired-candidate downgrade, the full quality-state matrix, reflection boundary guarantees and
 * a source scan proving the package never reads a wall clock.
 */
class MarketInterpretationSnapshotAssemblerTest {

    private final MarketInterpretationSnapshotAssembler assembler = new MarketInterpretationSnapshotAssembler();

    @Test
    void rejectsNullInputs() {
        MarketFeaturesSnapshot snapshot = AssemblyFixtures.bullishSnapshot();
        QualityAssessment qa = quality(snapshot);

        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(null, qa, POLICY));
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(snapshot, null, POLICY));
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(snapshot, qa, null));
    }

    // ------------------------------------------------------------------ mapping (17.5)

    @Test
    void mapsEveryComponentVerbatimIntoTheAggregate() {
        MarketFeaturesSnapshot snapshot = AssemblyFixtures.bullishSnapshot();
        QualityAssessment qa = quality(snapshot);

        MarketInterpretationSnapshot assembled = assembler.assemble(snapshot, qa, POLICY);

        // identity, verbatim from the feature snapshot
        assertEquals("binance", assembled.exchange());
        assertEquals("spot", assembled.marketType());
        assertEquals("BTC", assembled.base());
        assertEquals("USDT", assembled.quote());
        assertEquals("BTCUSDT", assembled.symbol());
        assertEquals("binance:spot:BTCUSDT", assembled.instrumentId());
        // timing: evaluatedAt is the source evaluation tick; validUntil the resolved deadline
        assertEquals(EVENT_TIME, assembled.evaluatedAt());
        assertEquals(assembled.featureLineage().sourceEvaluationAt(), assembled.evaluatedAt());
        assertEquals(EVENT_TIME.plusMillis(400), assembled.validUntil(), "H5S candidate: 500 − 100 buffer");
        // interpretation components, exactly the Stage 8 evaluation of the same inputs
        MarketOpportunityEvaluation evaluation =
                AssemblyFixtures.OPPORTUNITY_EVALUATOR.evaluate(snapshot, qa, OPPORTUNITY_POLICY);
        assertEquals(evaluation.qualityAssessment().interpretationQuality(), assembled.interpretationQuality());
        assertEquals(evaluation.crossHorizonEvaluation().horizonAssessments().asList(),
                assembled.horizonAssessments(), "exact values in canonical order");
        assertEquals(evaluation.crossHorizonEvaluation().crossHorizonAssessment(),
                assembled.crossHorizonAssessment());
        assertEquals(evaluation.marketOpportunity(), assembled.marketOpportunity(),
                "an active candidate is mapped unchanged");
        // lineage
        assertEquals(FeatureLineageFactory.from(snapshot), assembled.featureLineage());
        assertEquals(POLICY.interpretationLineage(), assembled.interpretationLineage());
    }

    @Test
    void featureLineageIsLossless() {
        FeatureLineage lineage = assembler.assemble(AssemblyFixtures.bullishSnapshot(),
                quality(AssemblyFixtures.bullishSnapshot()), POLICY).featureLineage();

        assertEquals("snap-1", lineage.sourceFeatureEventId());
        assertEquals(1, lineage.sourceFeatureSchemaVersion());
        assertEquals("mfs-features-v2", lineage.sourceFeatureSetVersion());
        assertEquals("cfg-test-mfs-v2", lineage.sourceFeatureConfigHash());
        assertEquals(EVENT_TIME, lineage.sourceEvaluationAt());
        assertEquals(EVENT_TIME.plusMillis(25), lineage.sourceComputedAt());
        assertEquals("TRADE", lineage.sourceTriggerSource());
    }

    // ------------------------------------------------------------------ id determinism (17.6)

    @Test
    void repeatedAssemblyIsValueEqualWithTheSameDeterministicId() {
        MarketFeaturesSnapshot snapshot = AssemblyFixtures.bullishSnapshot();

        MarketInterpretationSnapshot first = assembler.assemble(snapshot, quality(snapshot), POLICY);
        MarketInterpretationSnapshot second =
                new MarketInterpretationSnapshotAssembler().assemble(snapshot, quality(snapshot), POLICY);

        assertEquals(first, second);
        assertEquals(first.interpretationSnapshotId(), second.interpretationSnapshotId());
        assertEquals(InterpretationSnapshotIdGenerator.generate(first.featureLineage(), first.interpretationLineage()),
                first.interpretationSnapshotId(), "the id comes only from the existing generator");
    }

    @Test
    void idFollowsTheLineageOnly() {
        MarketFeaturesSnapshot snapshot = AssemblyFixtures.bullishSnapshot();
        String baseline = assembler.assemble(snapshot, quality(snapshot), POLICY).interpretationSnapshotId();

        MarketInterpretationAssemblyPolicy otherVersion = new MarketInterpretationAssemblyPolicy(
                "mse-interpretation-fixture-v2", POLICY.interpretationConfigHash(),
                OPPORTUNITY_POLICY, AssemblyFixtures.VALIDITY_POLICY);
        assertNotEquals(baseline,
                assembler.assemble(snapshot, quality(snapshot), otherVersion).interpretationSnapshotId(),
                "interpretation version changes the id");

        MarketInterpretationAssemblyPolicy otherHash = new MarketInterpretationAssemblyPolicy(
                POLICY.interpretationVersion(), "cfg-interpretation-fixture-2",
                OPPORTUNITY_POLICY, AssemblyFixtures.VALIDITY_POLICY);
        assertNotEquals(baseline,
                assembler.assemble(snapshot, quality(snapshot), otherHash).interpretationSnapshotId(),
                "interpretation config hash changes the id");

        MarketFeaturesSnapshot otherSource = snapshot.toBuilder().snapshotId("snap-2").build();
        assertNotEquals(baseline,
                assembler.assemble(otherSource, quality(otherSource), POLICY).interpretationSnapshotId(),
                "source feature event id changes the id");
    }

    // ------------------------------------------------------------------ quality states (17.7)

    @Test
    void qualityStateMatrixEndToEnd() {
        // OK + candidate
        MarketInterpretationSnapshot ok = assembler.assemble(AssemblyFixtures.bullishSnapshot(),
                quality(AssemblyFixtures.bullishSnapshot()), POLICY);
        assertEquals(InterpretationQualityStatus.OK, ok.interpretationQuality().status());
        assertEquals(OpportunityStatus.CANDIDATE, ok.marketOpportunity().status());

        // DEGRADED eligible + valid candidate (degraded validity adjustment applied)
        MarketFeaturesSnapshot degraded = AssemblyFixtures.degradedEligibleSnapshot();
        MarketInterpretationSnapshot degradedActive = assembler.assemble(degraded, quality(degraded), POLICY);
        assertEquals(InterpretationQualityStatus.DEGRADED, degradedActive.interpretationQuality().status());
        assertTrue(degradedActive.isEligibleForTrading());
        assertEquals(OpportunityStatus.CANDIDATE, degradedActive.marketOpportunity().status());
        assertEquals(EVENT_TIME.plusMillis(350), degradedActive.validUntil(), "500 − 100 − 50");

        // DEGRADED eligible + expired candidate → downgraded NO_OPPORTUNITY (deadline 350 reached)
        MarketInterpretationSnapshot degradedExpired =
                assembler.assemble(degraded, quality(degraded, EVENT_TIME.plusMillis(350)), POLICY);
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, degradedExpired.marketOpportunity().status());

        // DEGRADED non-eligible (history gap has eligible horizons, so use NO_DATA/UNSAFE/stale below)
        for (MarketFeaturesSnapshot blockedSource : List.of(
                AssemblyFixtures.unsafeSnapshot(), AssemblyFixtures.noDataSnapshot())) {
            MarketInterpretationSnapshot blocked = assembler.assemble(blockedSource, quality(blockedSource), POLICY);
            assertFalse(blocked.isEligibleForTrading());
            assertEquals(OpportunityStatus.BLOCKED, blocked.marketOpportunity().status());
            assertEquals(EVENT_TIME.plusMillis(150), blocked.validUntil(), "blocked base 250 − 100");
        }

        // stale assessment and clock skew → BLOCKED
        MarketFeaturesSnapshot bullish = AssemblyFixtures.bullishSnapshot();
        MarketInterpretationSnapshot stale =
                assembler.assemble(bullish, quality(bullish, EVENT_TIME.plusMillis(2_500)), POLICY);
        assertEquals(OpportunityStatus.BLOCKED, stale.marketOpportunity().status());
        MarketInterpretationSnapshot skewed =
                assembler.assemble(bullish, quality(bullish, EVENT_TIME.minusMillis(10)), POLICY);
        assertEquals(OpportunityStatus.BLOCKED, skewed.marketOpportunity().status());
        assertFalse(skewed.isEligibleForTrading(),
                "a snapshot with non-eligible quality never carries a candidate");
    }

    // ------------------------------------------------------------------ e2e scenarios (17.10)

    @Test
    void bullishAndBearishContinuationAssembleActiveCandidates() {
        MarketInterpretationSnapshot bullish = assembler.assemble(AssemblyFixtures.bullishSnapshot(),
                quality(AssemblyFixtures.bullishSnapshot()), POLICY);
        assertEquals(OpportunityType.MOMENTUM_CONTINUATION, bullish.marketOpportunity().type());
        assertEquals(OpportunitySide.LONG, bullish.marketOpportunity().side());
        assertEquals(MarketHorizon.H5S, bullish.marketOpportunity().setupHorizon());
        assertEquals(EVENT_TIME.plusMillis(400), bullish.validUntil());

        MarketInterpretationSnapshot bearish = assembler.assemble(AssemblyFixtures.bearishSnapshot(),
                quality(AssemblyFixtures.bearishSnapshot()), POLICY);
        assertEquals(OpportunitySide.SHORT, bearish.marketOpportunity().side());
        assertEquals(MarketHorizon.H5S, bearish.marketOpportunity().setupHorizon());
    }

    @Test
    void negativeInterpretationsAssembleNoOpportunitySnapshots() {
        for (MarketFeaturesSnapshot snapshot : List.of(
                AssemblyFixtures.partialSnapshot(),      // partial alignment
                AssemblyFixtures.conflictSnapshot(),     // structural conflict
                AssemblyFixtures.historyGapSnapshot(),   // insufficient senior context
                AssemblyFixtures.adverseBookSnapshot())) {
            MarketInterpretationSnapshot assembled = assembler.assemble(snapshot, quality(snapshot), POLICY);

            assertEquals(OpportunityStatus.NO_OPPORTUNITY, assembled.marketOpportunity().status());
            assertEquals(EVENT_TIME.plusMillis(200), assembled.validUntil(), "no-opportunity base 300 − 100");
            assertTrue(assembled.isEligibleForTrading());
        }
    }

    @Test
    void expiredCandidateAssemblesAsDowngradedNoOpportunity() {
        MarketFeaturesSnapshot snapshot = AssemblyFixtures.bullishSnapshot();
        // candidate deadline is T+400; the quality layer assessed at exactly that instant
        QualityAssessment lateQuality = quality(snapshot, EVENT_TIME.plusMillis(400));

        MarketInterpretationSnapshot assembled = assembler.assemble(snapshot, lateQuality, POLICY);

        MarketOpportunity opportunity = assembled.marketOpportunity();
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, opportunity.status());
        assertEquals(List.of(OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY,
                        InterpretationValidityReasonCodes.OPPORTUNITY_EXPIRED_BEFORE_ASSEMBLY),
                opportunity.reasonCodes());
        assertEquals(EVENT_TIME.plusMillis(200), assembled.validUntil(), "re-derived from the no-opportunity base");
        assertTrue(assembled.validUntil().isAfter(assembled.evaluatedAt()), "the snapshot stays domain-valid");
    }

    @Test
    void volatileCandidateGetsTheVolatileValidityAdjustment() {
        MarketFeaturesSnapshot snapshot = AssemblyFixtures.volatileSnapshot();
        QualityAssessment qa = quality(snapshot);

        // volatile continuation blocked by the default policy
        assertEquals(OpportunityStatus.NO_OPPORTUNITY,
                assembler.assemble(snapshot, qa, POLICY).marketOpportunity().status());

        // explicitly allowed: candidate with 500 − 100 − 25
        MarketInterpretationSnapshot allowed = assembler.assemble(snapshot, qa, ALLOW_VOLATILE_POLICY);
        assertEquals(OpportunityStatus.CANDIDATE, allowed.marketOpportunity().status());
        assertEquals(EVENT_TIME.plusMillis(375), allowed.validUntil());
    }

    @Test
    void mismatchedSnapshotAndQualityFailFastThroughTheConsistencyGuard() {
        MarketFeaturesSnapshot full = AssemblyFixtures.bullishSnapshot();
        QualityAssessment gapQuality = quality(AssemblyFixtures.historyGapSnapshot());

        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(full, gapQuality, POLICY));
    }

    // ------------------------------------------------------------------ safe boundary (17.8)

    @Test
    void assemblerHasExactlyOneSafePublicEntryPoint() {
        Method[] publicMethods = Arrays.stream(MarketInterpretationSnapshotAssembler.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .toArray(Method[]::new);
        assertEquals(1, publicMethods.length, "exactly one public business method");
        assertEquals(List.of(MarketFeaturesSnapshot.class, QualityAssessment.class,
                        MarketInterpretationAssemblyPolicy.class),
                List.of(publicMethods[0].getParameterTypes()),
                "the public path starts from the snapshot and its quality assessment only");

        Set<Class<?>> forbidden = Set.of(HorizonAssessments.class, CrossHorizonAssessment.class,
                CrossHorizonEvaluation.class, MarketOpportunity.class, MarketOpportunityEvaluation.class,
                FeatureLineage.class);
        for (Method method : MarketInterpretationSnapshotAssembler.class.getMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(forbidden.contains(parameter),
                        "public API must not accept independently produced interpretation objects: " + method);
            }
        }
    }

    @Test
    void validityResolverAndResolutionAreNotPublic() {
        assertFalse(Modifier.isPublic(InterpretationValidityResolver.class.getModifiers()),
                "the validity resolver must stay package-private");
        for (Method method : InterpretationValidityResolver.class.getDeclaredMethods()) {
            assertFalse(Modifier.isPublic(method.getModifiers()), "no resolver method may be public: " + method);
        }
        assertFalse(Modifier.isPublic(ValidityResolution.class.getModifiers()),
                "the validity resolution must stay package-private");
    }

    // ------------------------------------------------------------------ no wall clock (17.9)

    @Test
    void assemblyPackageNeverReadsAWallClock() throws Exception {
        Path packageDir = Path.of("src", "main", "java", "com", "trading", "marketsignalengine",
                "application", "domain", "interpretation", "assembly");
        assertTrue(Files.isDirectory(packageDir), "assembly sources expected at " + packageDir.toAbsolutePath());

        Pattern forbidden = Pattern.compile(
                "Instant\\s*\\.\\s*now|System\\s*\\.\\s*currentTimeMillis|new\\s+java\\.util\\.Date|new\\s+Date\\s*\\(|java\\.time\\.Clock|\\bClock\\b");
        try (var sources = Files.list(packageDir)) {
            List<Path> files = sources.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(files.isEmpty(), "no sources found in " + packageDir.toAbsolutePath());
            for (Path file : files) {
                String content = Files.readString(file);
                assertFalse(forbidden.matcher(content).find(),
                        file.getFileName() + " must not read a wall clock or reference one");
            }
        }
    }
}
