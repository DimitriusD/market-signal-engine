package com.trading.marketsignalengine.application.domain.model;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record SignalConfiguration(
        String signalSetVersion,
        BigDecimal maxSpreadBps,
        BigDecimal buySignedTradeFlow5sThreshold,
        BigDecimal sellSignedTradeFlow5sThreshold,
        BigDecimal buyBookImbalanceThreshold,
        BigDecimal sellBookImbalanceThreshold,
        BigDecimal maxShortTermVolatility1s) {

    public static SignalConfiguration defaults() {
        return SignalConfiguration.builder()
                .signalSetVersion("mse-signals-v1")
                .maxSpreadBps(new BigDecimal("2.0"))
                .buySignedTradeFlow5sThreshold(BigDecimal.ZERO)
                .sellSignedTradeFlow5sThreshold(BigDecimal.ZERO)
                .buyBookImbalanceThreshold(new BigDecimal("0.60"))
                .sellBookImbalanceThreshold(new BigDecimal("-0.60"))
                .maxShortTermVolatility1s(new BigDecimal("0.01"))
                .build();
    }
}
