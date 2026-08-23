package com.trading.marketsignalengine.application.domain.model.feature;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.Builder;

/**
 * Multi-horizon trade flow from MFS v2: one {@link TradeFlowWindow} per rolling window. Rules that
 * read a window must treat a {@code null} window or a {@code null} field as "unavailable", never as a
 * flat/zero flow. Today only the 5s window (and the 1s window for range validation) drives a V1
 * signal; 15s/60s are carried for lineage and for the V2 per-horizon evaluators.
 *
 * <p>{@link #window(MarketHorizon)} is the <b>single canonical</b> horizon → window selection
 * ({@code 1S → window1s, 5S → window5s, 15S → window15s, 60S → window60s}); availability resolution
 * and the V2 evaluators both read it so they can never disagree about which window backs a horizon.
 */
@Builder(toBuilder = true)
public record TradeFlowFeature(
        BigDecimal lastTradePrice,
        TradeFlowWindow window1s,
        TradeFlowWindow window5s,
        TradeFlowWindow window15s,
        TradeFlowWindow window60s) {

    /** The rolling window backing {@code horizon}; {@code null} when that window is absent. */
    public TradeFlowWindow window(MarketHorizon horizon) {
        Objects.requireNonNull(horizon, "horizon");
        return switch (horizon) {
            case H1S -> window1s;
            case H5S -> window5s;
            case H15S -> window15s;
            case H60S -> window60s;
        };
    }
}
