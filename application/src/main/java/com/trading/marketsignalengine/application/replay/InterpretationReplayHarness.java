package com.trading.marketsignalengine.application.replay;

import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.service.ValidatedMarketInterpretationEvaluator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * In-process V2 replay: {@code List<MarketFeaturesSnapshot> → List<MarketInterpretationSnapshot>}
 * through the <b>same validated evaluator the live path uses</b>
 * ({@link ValidatedMarketInterpretationEvaluator}: validator → quality resolver → assembler), one
 * output per input, in input order. No Kafka, no Spring, no I/O, no metrics, no publishing and no
 * wall-clock reads — the assessment instant is always explicit. A snapshot the validator rejects
 * live is rejected identically here ({@code InvalidMarketFeaturesSnapshotException}) — replay never
 * bypasses validation.
 *
 * <p>Live/replay parity: the same snapshot + {@code assessedAt} + policies produce a value-equal
 * {@link MarketInterpretationSnapshot} with the same deterministic id in both paths, because both go
 * through the one evaluator. Replay is deterministic only if the assessment instant is derived from
 * the recorded input rather than the wall clock (it feeds validity); the default resolver
 * ({@link #computedAtOrEventTime()}) pins it to the upstream compute time — the closest recorded
 * proxy for "when the engine would have seen this snapshot". A live run has additional transport
 * latency on top, which replay cannot and does not reproduce.
 */
public final class InterpretationReplayHarness {

    /** Default allowlist when none is given: the feature set MFS v2 publishes. */
    public static final Set<String> DEFAULT_SUPPORTED_FEATURE_SET_VERSIONS = Set.of("mfs-features-v2");

    private final ValidatedMarketInterpretationEvaluator evaluator;

    public InterpretationReplayHarness(ValidatedMarketInterpretationEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public ValidatedMarketInterpretationEvaluator evaluator() {
        return evaluator;
    }

    /** Replays with {@link #computedAtOrEventTime()} as the assessment-instant resolver. */
    public List<MarketInterpretationSnapshot> replay(List<MarketFeaturesSnapshot> inputs) {
        return replay(inputs, computedAtOrEventTime());
    }

    public List<MarketInterpretationSnapshot> replay(List<MarketFeaturesSnapshot> inputs,
                                                     Function<MarketFeaturesSnapshot, Instant> assessedAt) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(assessedAt, "assessedAt");
        List<MarketInterpretationSnapshot> outputs = new ArrayList<>(inputs.size());
        for (MarketFeaturesSnapshot input : inputs) {
            // A null input is a contract error reported by the validator (same as live), so the
            // resolver is not consulted for it; the evaluator throws InvalidMarketFeaturesSnapshotException.
            Instant at = input == null ? null : assessedAt.apply(input);
            if (input != null && at == null) {
                throw new IllegalArgumentException(
                        "assessedAt resolver returned null for snapshotId=" + input.snapshotId());
            }
            outputs.add(evaluator.evaluate(input, at));
        }
        return List.copyOf(outputs);
    }

    /** Fixed assessment instant for every input (golden tests, what-if runs). */
    public static Function<MarketFeaturesSnapshot, Instant> fixed(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return features -> instant;
    }

    /** {@code computedAt} of the input, falling back to {@code eventTime} when absent. */
    public static Function<MarketFeaturesSnapshot, Instant> computedAtOrEventTime() {
        return features -> features.computedAt() != null ? features.computedAt() : features.eventTime();
    }
}
