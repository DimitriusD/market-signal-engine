package com.trading.marketsignalengine.application.service;

import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationAssemblyPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationSnapshotAssembler;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessmentResolver;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityEligibilityPolicy;
import com.trading.marketsignalengine.application.domain.validation.InvalidMarketFeaturesSnapshotException;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.time.Instant;
import java.util.Objects;

/**
 * The one validated V2 evaluation step shared by the live handle path and the replay harness:
 * {@code validate(snapshot) → resolve quality(snapshot, assessedAt) → assemble interpretation}. Both
 * callers go through this class, so a snapshot the validator rejects in Kafka is rejected
 * identically in replay, and a snapshot the engine interprets live is interpreted by exactly the
 * same Stage 3–9 code in replay. Invariant: the same {@link MarketFeaturesSnapshot} +
 * {@code assessedAt} + policies always yield a value-equal {@link MarketInterpretationSnapshot} with
 * the same deterministic id, or the same validation exception.
 *
 * <p>Deliberately narrow: validation always runs <em>before</em> any interpretation; no publishing,
 * no metrics, no clock and no wall-clock reads (the caller decides what "now" is — the injected
 * clock's instant live, a recorded instant in replay), no Spring/Kafka/Avro, and no horizon / cross /
 * opportunity evaluation outside the assembler (which runs the full pipeline exactly once).
 */
public final class ValidatedMarketInterpretationEvaluator {

    private final MarketFeaturesSnapshotValidator validator;
    private final QualityAssessmentResolver qualityResolver;
    private final MarketInterpretationSnapshotAssembler assembler;
    private final QualityEligibilityPolicy qualityPolicy;
    private final MarketInterpretationAssemblyPolicy assemblyPolicy;

    public ValidatedMarketInterpretationEvaluator(MarketFeaturesSnapshotValidator validator,
                                                 QualityAssessmentResolver qualityResolver,
                                                 MarketInterpretationSnapshotAssembler assembler,
                                                 QualityEligibilityPolicy qualityPolicy,
                                                 MarketInterpretationAssemblyPolicy assemblyPolicy) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.qualityResolver = Objects.requireNonNull(qualityResolver, "qualityResolver");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.qualityPolicy = Objects.requireNonNull(qualityPolicy, "qualityPolicy");
        this.assemblyPolicy = Objects.requireNonNull(assemblyPolicy, "assemblyPolicy");
    }

    public MarketFeaturesSnapshotValidator validator() {
        return validator;
    }

    public MarketInterpretationAssemblyPolicy assemblyPolicy() {
        return assemblyPolicy;
    }

    /**
     * @throws InvalidMarketFeaturesSnapshotException when {@code snapshot} is null or violates the
     *         MFS v2 contract (never reaches the interpretation pipeline)
     * @throws IllegalArgumentException when {@code assessedAt} is null (never reaches the pipeline)
     */
    public MarketInterpretationSnapshot evaluate(MarketFeaturesSnapshot snapshot, Instant assessedAt) {
        validator.validate(snapshot);
        if (assessedAt == null) {
            throw new IllegalArgumentException(
                    "assessedAt must not be null (snapshotId=" + snapshot.snapshotId() + ")");
        }
        QualityAssessment qualityAssessment = qualityResolver.resolve(snapshot, assessedAt, qualityPolicy);
        return assembler.assemble(snapshot, qualityAssessment, assemblyPolicy);
    }
}
