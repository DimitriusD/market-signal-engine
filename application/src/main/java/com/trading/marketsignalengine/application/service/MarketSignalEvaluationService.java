package com.trading.marketsignalengine.application.service;

import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.port.input.EvaluateMarketSignalsUseCase;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.application.port.output.SignalConfigurationProviderPort;
import java.time.Instant;
import java.util.Objects;

public class MarketSignalEvaluationService implements EvaluateMarketSignalsUseCase {

    private final MarketSignalEngine marketSignalEngine;
    private final SignalConfigurationProviderPort configurationProvider;
    private final MarketSignalSnapshotPublisherPort publisher;

    public MarketSignalEvaluationService(
            MarketSignalEngine marketSignalEngine,
            SignalConfigurationProviderPort configurationProvider,
            MarketSignalSnapshotPublisherPort publisher) {
        this.marketSignalEngine = marketSignalEngine;
        this.configurationProvider = configurationProvider;
        this.publisher = publisher;
    }

    @Override
    public MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features) {
        Objects.requireNonNull(features, "features");

        SignalConfiguration configuration =
                configurationProvider.getConfiguration(features.exchange(), features.symbol());
        SignalEvaluationContext context =
                new SignalEvaluationContext(features, configuration, Instant.now());

        MarketSignalSnapshot snapshot = marketSignalEngine.evaluate(context);
        publisher.publish(snapshot);
        return snapshot;
    }
}
