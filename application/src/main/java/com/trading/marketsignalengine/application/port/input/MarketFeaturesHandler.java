package com.trading.marketsignalengine.application.port.input;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;

public interface MarketFeaturesHandler {

    void handle(MarketFeaturesSnapshot features);
}
