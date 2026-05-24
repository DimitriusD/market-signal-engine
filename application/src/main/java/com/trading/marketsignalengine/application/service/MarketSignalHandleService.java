package com.trading.marketsignalengine.application.service;

import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MarketSignalHandleService implements MarketFeaturesHandler {

    private final MarketSignalEngine marketSignalEngine;
    private final SignalConfiguration signalConfiguration;
    private final MarketSignalSnapshotPublisherPort publisher;
    private final Clock clock;

    public MarketSignalHandleService(
            MarketSignalEngine marketSignalEngine,
            SignalConfiguration signalConfiguration,
            MarketSignalSnapshotPublisherPort publisher,
            Clock clock) {
        this.marketSignalEngine = marketSignalEngine;
        this.signalConfiguration = signalConfiguration;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public void handle(MarketFeaturesSnapshot features) {
        Objects.requireNonNull(features, "features");

        SignalEvaluationContext context = new SignalEvaluationContext(
                features, signalConfiguration, Instant.now(clock));

        MarketSignalSnapshot snapshot = marketSignalEngine.evaluate(context);
        publisher.publish(snapshot);

        log.info(
                "Market signal snapshot evaluated and published: sourceFeatureSnapshotId={}, signalSnapshotId={}, exchange={}, symbol={}, instrumentId={}, marketBias={}, riskLevel={}",
                features.snapshotId(),
                snapshot.signalSnapshotId(),
                snapshot.exchange(),
                snapshot.symbol(),
                snapshot.instrumentId(),
                snapshot.marketBias(),
                snapshot.riskLevel());
    }
}
