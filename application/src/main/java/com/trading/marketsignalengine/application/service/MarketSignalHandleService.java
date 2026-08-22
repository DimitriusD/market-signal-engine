package com.trading.marketsignalengine.application.service;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.application.port.output.SignalMetricsPort;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * validate → evaluate → publish, reporting durations and outcomes to {@link SignalMetricsPort}.
 * Validation failures propagate (the transport adapter routes them to the DLT); publish failures
 * are reported then re-thrown so the transport can retry/recover with bounded behaviour.
 */
@Slf4j
public class MarketSignalHandleService implements MarketFeaturesHandler {

    private final MarketSignalEngine marketSignalEngine;
    private final MarketSignalSnapshotPublisherPort publisher;
    private final MarketFeaturesSnapshotValidator validator;
    private final SignalMetricsPort metrics;

    public MarketSignalHandleService(
            MarketSignalEngine marketSignalEngine,
            MarketSignalSnapshotPublisherPort publisher,
            MarketFeaturesSnapshotValidator validator) {
        this(marketSignalEngine, publisher, validator, SignalMetricsPort.NOOP);
    }

    public MarketSignalHandleService(
            MarketSignalEngine marketSignalEngine,
            MarketSignalSnapshotPublisherPort publisher,
            MarketFeaturesSnapshotValidator validator,
            SignalMetricsPort metrics) {
        this.marketSignalEngine = Objects.requireNonNull(marketSignalEngine, "marketSignalEngine");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public void handle(MarketFeaturesSnapshot features) {
        long evaluationStart = System.nanoTime();
        validator.validate(features);
        MarketSignalSnapshot snapshot = marketSignalEngine.evaluate(features);
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
