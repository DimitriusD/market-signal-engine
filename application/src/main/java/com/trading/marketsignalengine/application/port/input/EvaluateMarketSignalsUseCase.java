package com.trading.marketsignalengine.application.port.input;

import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;

public interface EvaluateMarketSignalsUseCase {

    MarketSignalSnapshot evaluate(MarketFeaturesSnapshot features);
}
