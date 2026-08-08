package com.trading.marketsignalengine.application.domain.model.feature;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record TradeFlowFeature(
        BigDecimal lastTradePrice,

        BigDecimal buyAggressiveVolume1s,
        BigDecimal sellAggressiveVolume1s,
        BigDecimal totalAggressiveVolume1s,
        BigDecimal signedTradeFlow1s,
        BigDecimal signedFlowImbalance1s,
        int tradeCount1s,
        BigDecimal tradeIntensity1s,
        BigDecimal avgTradeSize1s,
        BigDecimal vwap1s,

        BigDecimal buyAggressiveVolume5s,
        BigDecimal sellAggressiveVolume5s,
        BigDecimal totalAggressiveVolume5s,
        BigDecimal signedTradeFlow5s,
        BigDecimal signedFlowImbalance5s,
        int tradeCount5s,
        BigDecimal tradeIntensity5s,
        BigDecimal avgTradeSize5s,
        BigDecimal vwap5s) {
}
