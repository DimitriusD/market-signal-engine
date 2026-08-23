package com.trading.marketsignalengine.application.replay;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.service.StandardSignalEngine;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.service.ValidatedMarketSignalEvaluator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * In-process replay: {@code List<MarketFeaturesSnapshot> → List<MarketSignalSnapshot>} through the
 * <b>same validated evaluator the live path uses</b> ({@link ValidatedMarketSignalEvaluator}:
 * {@link MarketFeaturesSnapshotValidator} → production engine), one output per input, in input order.
 * No Kafka, no Spring, no I/O, no metrics, no publishing. A snapshot the validator rejects live is
 * rejected identically here ({@code InvalidMarketFeaturesSnapshotException}) — replay never bypasses
 * validation.
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

    /** Default allowlist when none is given: the feature set MFS v2 publishes. */
    public static final Set<String> DEFAULT_SUPPORTED_FEATURE_SET_VERSIONS = Set.of("mfs-features-v2");

    private final ValidatedMarketSignalEvaluator evaluator;

    public ReplayHarness(ValidatedMarketSignalEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    /**
     * Harness over the canonical production wiring ({@link StandardSignalEngine}) and the default
     * validator allowlist ({@link #DEFAULT_SUPPORTED_FEATURE_SET_VERSIONS}).
     */
    public static ReplayHarness standard(SignalConfiguration configuration) {
        return standard(configuration, new MarketFeaturesSnapshotValidator(DEFAULT_SUPPORTED_FEATURE_SET_VERSIONS));
    }

    /**
     * Harness over the canonical production wiring with an explicit validator (e.g. a wider
     * allowlist for recorded data). The engine clock is a fixed dummy: replay only ever calls
     * {@code evaluate(features, evaluatedAt)} with an explicit instant, so the wall clock is never read.
     */
    public static ReplayHarness standard(SignalConfiguration configuration, MarketFeaturesSnapshotValidator validator) {
        Clock neverConsulted = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        return new ReplayHarness(new ValidatedMarketSignalEvaluator(
                validator, StandardSignalEngine.create(configuration, neverConsulted)));
    }

    public ValidatedMarketSignalEvaluator evaluator() {
        return evaluator;
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
            // A null input is a contract error reported by the validator (same as live), so the
            // resolver is not consulted for it; the evaluator throws InvalidMarketFeaturesSnapshotException.
            Instant at = input == null ? null : evaluatedAt.apply(input);
            if (input != null && at == null) {
                throw new IllegalArgumentException(
                        "evaluatedAt resolver returned null for snapshotId=" + input.snapshotId());
            }
            outputs.add(evaluator.evaluate(input, at));
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
