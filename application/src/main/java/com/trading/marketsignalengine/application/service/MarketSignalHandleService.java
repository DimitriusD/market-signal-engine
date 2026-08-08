package com.trading.marketsignalengine.application.service;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MarketSignalHandleService implements MarketFeaturesHandler {

    private final MarketSignalEngine marketSignalEngine;
    private final MarketSignalSnapshotPublisherPort publisher;
    private final MarketFeaturesSnapshotValidator validator;

    public MarketSignalHandleService(
            MarketSignalEngine marketSignalEngine,
            MarketSignalSnapshotPublisherPort publisher,
            MarketFeaturesSnapshotValidator validator) {
        this.marketSignalEngine = marketSignalEngine;
        this.publisher = publisher;
        this.validator = validator;
    }

    @Override
    public void handle(MarketFeaturesSnapshot features) {
        validator.validate(features);
        MarketSignalSnapshot snapshot = marketSignalEngine.evaluate(features);
        publisher.publish(snapshot);
    }
}
