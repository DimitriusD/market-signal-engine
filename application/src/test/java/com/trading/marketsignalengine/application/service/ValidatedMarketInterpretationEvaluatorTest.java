package com.trading.marketsignalengine.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationSnapshotAssembler;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessmentResolver;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.validation.InvalidMarketFeaturesSnapshotException;
import org.junit.jupiter.api.Test;

/**
 * The one validated V2 evaluation step: validation strictly before interpretation, explicit
 * assessment instant, exactly the Stage 3–9 pipeline underneath, deterministic value-equal output
 * with the same id for the same inputs.
 */
class ValidatedMarketInterpretationEvaluatorTest {

    private final ValidatedMarketInterpretationEvaluator evaluator = RuntimeFixtures.evaluator();

    @Test
    void validationRunsBeforeAnyInterpretation() {
        // an invalid snapshot fails as a validation error even with a null assessedAt — the
        // interpretation pipeline (which would reject the null instant differently) is never reached
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> evaluator.evaluate(RuntimeFixtures.invalidSnapshot(), null));
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> evaluator.evaluate(RuntimeFixtures.invalidSnapshot(), RuntimeFixtures.ASSESSED_AT));
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> evaluator.evaluate(null, RuntimeFixtures.ASSESSED_AT));
    }

    @Test
    void nullAssessedAtOnAValidSnapshotIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(RuntimeFixtures.bullishSnapshot(), null));
        assertTrue(ex.getMessage().contains("assessedAt"), ex.getMessage());
    }

    @Test
    void producesExactlyTheAssembledInterpretationOfTheSameInputs() {
        MarketFeaturesSnapshot snapshot = RuntimeFixtures.bullishSnapshot();

        MarketInterpretationSnapshot viaEvaluator = evaluator.evaluate(snapshot, RuntimeFixtures.ASSESSED_AT);

        MarketInterpretationSnapshot direct = new MarketInterpretationSnapshotAssembler().assemble(snapshot,
                new QualityAssessmentResolver().resolve(snapshot, RuntimeFixtures.ASSESSED_AT,
                        RuntimeFixtures.QUALITY_POLICY),
                RuntimeFixtures.ASSEMBLY_POLICY);
        assertEquals(direct, viaEvaluator, "no interpretation runs outside the assembler pipeline");
        assertEquals(OpportunityStatus.CANDIDATE, viaEvaluator.marketOpportunity().status());
    }

    @Test
    void sameSnapshotAndInstantYieldValueEqualResultWithTheSameId() {
        MarketFeaturesSnapshot snapshot = RuntimeFixtures.bullishSnapshot();

        MarketInterpretationSnapshot first = evaluator.evaluate(snapshot, RuntimeFixtures.ASSESSED_AT);
        MarketInterpretationSnapshot second =
                RuntimeFixtures.evaluator().evaluate(snapshot, RuntimeFixtures.ASSESSED_AT);

        assertEquals(first, second);
        assertEquals(first.interpretationSnapshotId(), second.interpretationSnapshotId());
    }

    @Test
    void nonEligibleQualityYieldsABlockedInterpretation() {
        MarketInterpretationSnapshot blocked =
                evaluator.evaluate(RuntimeFixtures.unsafeSnapshot(), RuntimeFixtures.ASSESSED_AT);

        assertEquals(OpportunityStatus.BLOCKED, blocked.marketOpportunity().status());
        assertTrue(!blocked.isEligibleForTrading());
    }
}
