package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;

public interface MarketSignalEngine {

    MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features);
}
