package com.trading.marketsignalengine.config;

import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.port.output.SignalConfigurationProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PropertiesSignalConfigurationProvider implements SignalConfigurationProviderPort {

    private final SignalProperties signalProperties;

    @Override
    public SignalConfiguration getConfiguration(String exchange, String symbol) {
        return SignalConfiguration.builder()
                .signalSetVersion(signalProperties.getSignalSetVersion())
                .maxSpreadBps(signalProperties.getMaxSpreadBps())
                .buySignedTradeFlow5sThreshold(signalProperties.getBuySignedTradeFlow5sThreshold())
                .sellSignedTradeFlow5sThreshold(signalProperties.getSellSignedTradeFlow5sThreshold())
                .buyBookImbalanceThreshold(signalProperties.getBuyBookImbalanceThreshold())
                .sellBookImbalanceThreshold(signalProperties.getSellBookImbalanceThreshold())
                .maxShortTermVolatility1s(signalProperties.getMaxShortTermVolatility1s())
                .build();
    }
}
