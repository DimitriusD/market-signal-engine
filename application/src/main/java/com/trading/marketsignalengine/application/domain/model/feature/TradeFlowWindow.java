package com.trading.marketsignalengine.application.domain.model.feature;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * One rolling trade-flow window (1s / 5s / 15s / 60s) as published by MFS v2. Every field is
 * nullable: a {@code null} means "not available for this window" (warm-up, history gap, failed
 * calculator) and must never be read as zero. For the 1s/5s windows the upstream contract always
 * carries the counts; for 15s/60s they are optional.
 */
@Builder(toBuilder = true)
public record TradeFlowWindow(
        BigDecimal buyAggressiveVolume,
        BigDecimal sellAggressiveVolume,
        BigDecimal totalAggressiveVolume,
        BigDecimal signedTradeFlow,
        BigDecimal signedFlowImbalance,
        Integer tradeCount,
        Integer validQtyTradeCount,
        Integer aggressiveTradeCount,
        Integer unknownSideCount,
        BigDecimal tradeIntensity,
        BigDecimal avgTradeSize,
        BigDecimal vwap) {
}
