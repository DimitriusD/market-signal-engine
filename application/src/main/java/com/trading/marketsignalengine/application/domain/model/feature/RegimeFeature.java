package com.trading.marketsignalengine.application.domain.model.feature;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record RegimeFeature(
        BigDecimal lastTradeDistanceToMidBps,
        BigDecimal realizedVolatilityBps1s) {
}
