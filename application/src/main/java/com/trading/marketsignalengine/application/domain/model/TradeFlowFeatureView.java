package com.trading.marketsignalengine.application.domain.model;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record TradeFlowFeatureView(
        BigDecimal lastTradePrice,
        BigDecimal signedTradeFlow1s,
        BigDecimal signedTradeFlow5s,
        int tradeCount1s,
        BigDecimal tradeIntensity1s,
        BigDecimal avgTradeSize1s,
        BigDecimal vwap1s) {
}
