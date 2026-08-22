package com.trading.marketsignalengine.application.domain.model.feature;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * Multi-horizon trade flow from MFS v2: one {@link TradeFlowWindow} per rolling window. Rules that
 * read a window must treat a {@code null} window or a {@code null} field as "unavailable", never as a
 * flat/zero flow. Today only the 5s window (and the 1s window for range validation) drives a signal;
 * 15s/60s are carried for lineage and for the post-paper per-horizon work.
 */
@Builder(toBuilder = true)
public record TradeFlowFeature(
        BigDecimal lastTradePrice,
        TradeFlowWindow window1s,
        TradeFlowWindow window5s,
        TradeFlowWindow window15s,
        TradeFlowWindow window60s) {
}
