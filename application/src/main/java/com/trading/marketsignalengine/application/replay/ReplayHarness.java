package com.trading.marketsignalengine.application.replay;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.StandardSignalEngine;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * In-process replay: {@code List<MarketFeaturesSnapshot> → List<MarketSignalSnapshot>} through the
 * production engine, one output per input, in input order. No Kafka, no Spring, no I/O.
 *
 * <p>Replay is deterministic only if the evaluation instant is derived from the recorded input
 * rather than the wall clock, because {@code evaluatedAt} feeds {@code validUntil}. The default
 * resolver ({@link #computedAtOrEventTime()}) pins evaluation to the upstream compute time, which is
 * the closest recorded proxy for "when the engine would have seen this snapshot"; a live run has
 * additional transport latency on top, which replay cannot and does not reproduce.
 *
 * <p>Scope (path-to-paper-trading.md, Блок 2 п. 2.0): this is the regression harness for golden
 * tests. A datalake loader that feeds recorded MFS snapshots into it is a later, separate step.
 */
public final class ReplayHarness {

    private final MarketSignalEngine engine;

    public ReplayHarness(MarketSignalEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Builds a harness over the canonical production wiring ({@link StandardSignalEngine}). The
     * clock is only consulted by {@link MarketSignalEngine#evaluate(MarketFeaturesSnapshot)}, which
     * replay never calls; it is passed through so the engine instance is complete.
     */
    public static ReplayHarness standard(SignalConfiguration configuration) {
        return new ReplayHarness(StandardSignalEngine.create(configuration, Clock.systemUTC()));
    }

    /** Replays with {@link #computedAtOrEventTime()} as the evaluation-time resolver. */
    public List<MarketSignalSnapshot> replay(List<MarketFeaturesSnapshot> inputs) {
        return replay(inputs, computedAtOrEventTime());
    }

    public List<MarketSignalSnapshot> replay(List<MarketFeaturesSnapshot> inputs,
                                             Function<MarketFeaturesSnapshot, Instant> evaluatedAt) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        List<MarketSignalSnapshot> outputs = new ArrayList<>(inputs.size());
        for (MarketFeaturesSnapshot input : inputs) {
            Instant at = evaluatedAt.apply(input);
            if (at == null) {
                throw new IllegalArgumentException(
                        "evaluatedAt resolver returned null for snapshotId=" + input.snapshotId());
            }
            outputs.add(engine.evaluate(input, at));
        }
        return List.copyOf(outputs);
    }

    /** Fixed evaluation instant for every input (golden tests, what-if runs). */
    public static Function<MarketFeaturesSnapshot, Instant> fixed(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return features -> instant;
    }

    /** {@code computedAt} of the input, falling back to {@code eventTime} when absent. */
    public static Function<MarketFeaturesSnapshot, Instant> computedAtOrEventTime() {
        return features -> features.computedAt() != null ? features.computedAt() : features.eventTime();
    }
}
