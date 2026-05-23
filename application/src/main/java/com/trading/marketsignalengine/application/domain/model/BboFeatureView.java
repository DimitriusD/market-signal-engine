package com.trading.marketsignalengine.application.domain.model;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record BboFeatureView(
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
