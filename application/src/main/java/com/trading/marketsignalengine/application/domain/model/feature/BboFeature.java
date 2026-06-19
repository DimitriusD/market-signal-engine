package com.trading.marketsignalengine.application.domain.model.feature;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record BboFeature(
        BigDecimal bestBidPrice,
        BigDecimal bestAskPrice,
        BigDecimal bestBidQty,
        BigDecimal bestAskQty,
        BigDecimal spreadAbs,
        BigDecimal spreadBps,
        BigDecimal midPrice,
        BigDecimal micropriceTop1,
        BigDecimal micropriceOffsetBps) {
}
