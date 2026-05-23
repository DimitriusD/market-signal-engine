package com.trading.marketsignalengine.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.signal")
public class SignalProperties {

    private String signalSetVersion;
    private BigDecimal maxSpreadBps;
    private BigDecimal buySignedTradeFlow5sThreshold;
    private BigDecimal sellSignedTradeFlow5sThreshold;
    private BigDecimal buyBookImbalanceThreshold;
    private BigDecimal sellBookImbalanceThreshold;
    private BigDecimal maxShortTermVolatility1s;
}
