package com.trading.marketsignalengine.application.service;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.validation.InvalidMarketFeaturesSnapshotException;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import java.time.Instant;
import java.util.Objects;

/**
 * The one validated evaluation step shared by the live handle path and the replay harness:
 * {@code validate(features) → engine.evaluate(features, evaluatedAt)}. Both callers go through this
 * class, so a snapshot the validator rejects in Kafka is rejected identically in replay, and a
 * snapshot the engine evaluates live is evaluated by exactly the same code in replay. Invariant:
 * the same {@link MarketFeaturesSnapshot} + {@code evaluatedAt} + configuration always yields the
 * same {@link MarketSignalSnapshot} or the same validation exception.
 *
 * <p>Deliberately narrow: no publishing, no metrics, no clock (the caller decides what "now" is —
 * {@code Instant.now(clock)} live, a recorded instant in replay), no Spring/Kafka/Avro. Fail-fast:
 * {@code null} input is a contract error ({@link InvalidMarketFeaturesSnapshotException}), a
 * {@code null} {@code evaluatedAt} or a {@code null} engine result is a programming error and is
 * never turned into a "successful" empty result.
 */
public final class ValidatedMarketSignalEvaluator {

    private final MarketFeaturesSnapshotValidator validator;
    private final MarketSignalEngine engine;

    public ValidatedMarketSignalEvaluator(MarketFeaturesSnapshotValidator validator, MarketSignalEngine engine) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public MarketFeaturesSnapshotValidator validator() {
        return validator;
    }

    /**
     * @throws InvalidMarketFeaturesSnapshotException when {@code features} is null or violates the
     *         MFS v2 contract (never reaches the engine)
     * @throws IllegalArgumentException when {@code evaluatedAt} is null (never reaches the engine)
     * @throws IllegalStateException when the engine returned {@code null}
     */
    public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features, Instant evaluatedAt) {
        validator.validate(features);
        if (evaluatedAt == null) {
            throw new IllegalArgumentException(
                    "evaluatedAt must not be null (snapshotId=" + features.snapshotId() + ")");
        }
        MarketSignalSnapshot snapshot = engine.evaluate(features, evaluatedAt);
        if (snapshot == null) {
            throw new IllegalStateException(
                    "MarketSignalEngine returned null for snapshotId=" + features.snapshotId()
                            + " evaluatedAt=" + evaluatedAt);
        }
        return snapshot;
    }
}
