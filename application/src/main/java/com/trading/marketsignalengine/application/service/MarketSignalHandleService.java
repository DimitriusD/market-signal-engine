package com.trading.marketsignalengine.application.service;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.application.port.output.SignalMetricsPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Live handle path: {@code evaluatedAt = Instant.now(clock)} → {@link ValidatedMarketSignalEvaluator}
 * (validate → evaluate) → publish, reporting durations and outcomes to {@link SignalMetricsPort}.
 * The evaluation instant comes from the injected clock so there is no hidden wall-clock read inside
 * the application flow; replay feeds the same evaluator a recorded instant instead.
 *
 * <p>Nothing is caught or hidden: validation failures propagate (the transport adapter routes them to
 * the DLT), evaluation failures propagate, publish failures are reported to metrics then re-thrown so
 * the transport can retry / recover with bounded behaviour. A {@code null} output never reaches the
 * publisher — the evaluator fails fast before that.
 */
@Slf4j
public class MarketSignalHandleService implements MarketFeaturesHandler {

    private final ValidatedMarketSignalEvaluator evaluator;
    private final MarketSignalSnapshotPublisherPort publisher;
    private final Clock clock;
    private final SignalMetricsPort metrics;

    public MarketSignalHandleService(
            ValidatedMarketSignalEvaluator evaluator,
            MarketSignalSnapshotPublisherPort publisher,
            Clock clock) {
        this(evaluator, publisher, clock, SignalMetricsPort.NOOP);
    }

    public MarketSignalHandleService(
            ValidatedMarketSignalEvaluator evaluator,
            MarketSignalSnapshotPublisherPort publisher,
            Clock clock,
            SignalMetricsPort metrics) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public void handle(MarketFeaturesSnapshot features) {
        long evaluationStart = System.nanoTime();
        Instant evaluatedAt = Instant.now(clock);
        MarketSignalSnapshot snapshot = evaluator.evaluate(features, evaluatedAt);
        metrics.evaluated(features, snapshot, Duration.ofNanos(System.nanoTime() - evaluationStart));

        long publishStart = System.nanoTime();
        try {
            publisher.publish(snapshot);
        } catch (RuntimeException ex) {
            metrics.publishFailed(snapshot, Duration.ofNanos(System.nanoTime() - publishStart), ex);
            throw ex;
        }
        metrics.published(snapshot, Duration.ofNanos(System.nanoTime() - publishStart));
    }
}
