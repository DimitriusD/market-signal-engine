package com.trading.marketsignalengine.application.domain.availability;

import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;

/**
 * Rolling-window horizons MFS v2 publishes for windowed features (trade flow, regime). The wire
 * contract differs by horizon: for {@code 1S}/{@code 5S} the counters are non-nullable Avro
 * {@code int}s with schema default {@code 0}, so a zero count cannot by itself prove the window was
 * computed; for {@code 15S}/{@code 60S} the counters are {@code ["null","int"]} and {@code null} means
 * "window not covered / not computed" while {@code 0} is a genuinely measured empty window.
 */
public enum FeatureWindowHorizon {
    H1S("1S", 1_000L, false),
    H5S("5S", 5_000L, false),
    H15S("15S", 15_000L, true),
    H60S("60S", 60_000L, true);

    private final String label;
    private final long windowMs;
    private final boolean nullableCounts;

    FeatureWindowHorizon(String label, long windowMs, boolean nullableCounts) {
        this.label = label;
        this.windowMs = windowMs;
        this.nullableCounts = nullableCounts;
    }

    /** Contract label as used downstream ({@code 1S}, {@code 5S}, {@code 15S}, {@code 60S}). */
    public String label() {
        return label;
    }

    public long windowMs() {
        return windowMs;
    }

    /** True when the upstream counters are nullable on the wire ({@code null} = not computed). */
    public boolean nullableCounts() {
        return nullableCounts;
    }

    /** The trade-flow window of this horizon, or {@code null} when the feature group is absent. */
    public TradeFlowWindow tradeFlowWindowOf(TradeFlowFeature tradeFlow) {
        if (tradeFlow == null) {
            return null;
        }
        return switch (this) {
            case H1S -> tradeFlow.window1s();
            case H5S -> tradeFlow.window5s();
            case H15S -> tradeFlow.window15s();
            case H60S -> tradeFlow.window60s();
        };
    }
}
