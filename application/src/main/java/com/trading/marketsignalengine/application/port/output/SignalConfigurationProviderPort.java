package com.trading.marketsignalengine.application.port.output;

import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;

public interface SignalConfigurationProviderPort {

    SignalConfiguration getConfiguration(String exchange, String symbol);
}
