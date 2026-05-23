package com.trading.marketsignalengine.application.domain.model;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record RegimeFeatureView(
        BigDecimal lastTradeDistanceToMidBps,
        BigDecimal shortTermVolatility1s) {
}
