package com.trading.marketsignalengine.application.port.input;

import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;

public interface MarketFeaturesHandler {

    void handle(MarketFeaturesSnapshot features);
}
