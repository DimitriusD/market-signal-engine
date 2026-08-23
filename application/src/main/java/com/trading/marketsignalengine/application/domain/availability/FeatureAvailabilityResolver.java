package com.trading.marketsignalengine.application.domain.availability;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure, deterministic input-side resolver of rolling-window availability for the trade-flow feature
 * group (1S / 5S / 15S / 60S). It formalises what "the window is there" means on the MFS v2 wire so
 * that later multi-horizon logic never confuses {@code null} with zero, warm-up with no-data, or a
 * failed calculator with a neutral value. It does <b>not</b> influence the current V1 rules or golden
 * outputs; it is the input-normalisation foundation for the V2 domain stage.
 *
 * <h2>Presence markers (what proves a window was computed)</h2>
 * A window is <em>computed</em> when any nullable computed metric is non-null
 * ({@code buy/sell/totalAggressiveVolume}, {@code signedTradeFlow}, {@code signedFlowImbalance},
 * {@code tradeIntensity}, {@code avgTradeSize}, {@code vwap}) — a real numeric zero counts as present —
 * or, depending on the horizon's counter contract:
 * <ul>
 *   <li>{@code 15S}/{@code 60S}: counters are nullable on the wire; a non-null counter (even {@code 0},
 *       a measured empty window) proves computation; all-null counters mean "not covered".</li>
 *   <li>{@code 1S}/{@code 5S}: counters are non-nullable Avro ints with schema default {@code 0}, so only
 *       a <em>positive</em> counter proves computation. <b>Known producer limitation:</b> the current
 *       MFS v2 {@code TradeFlowFeatureCalculator} emits {@code tradeIntensity = null} (not {@code 0})
 *       when a covered window holds no trades, so a covered-but-empty 1S/5S window is indistinguishable
 *       on the wire from an uncovered one. The resolver is conservative and reports it as not computed
 *       (WARMING_UP / UNTRUSTED / UNAVAILABLE by the rules below); a producer-side explicit coverage
 *       marker is the clean fix and is tracked for the V2 stage.</li>
 * </ul>
 *
 * <h2>Precedence (first match wins, per horizon)</h2>
 * <ol>
 *   <li>{@link FeatureAvailabilityStatus#FAILED} — {@code diagnostics.failedFeatureGroups} contains
 *       {@code trade-flow}: every trade-flow horizon failed (the group was published as absent; a
 *       failure is never a neutral feature).</li>
 *   <li>{@link FeatureAvailabilityStatus#UNTRUSTED} — {@code quality.staleTrades} (the trades feeding
 *       every window are older than the freshness threshold) → all horizons; or
 *       {@code qualityReasons} contains {@code TRADE_HISTORY_GAP} → the horizons whose window was
 *       <em>not</em> computed (MFS v2 leaves exactly the windows spanning the gap uncovered and
 *       computes the rest from complete history, see {@code MarketView#isTradeWindowCovered}).</li>
 *   <li>{@link FeatureAvailabilityStatus#WARMING_UP} — window not computed and
 *       {@code quality.warmingUp = true}. Warm-up is global (first trade &lt; 60s ago) but short
 *       windows are computed as soon as their own length is covered, so 1S/5S can be AVAILABLE while
 *       15S/60S are still WARMING_UP.</li>
 *   <li>{@link FeatureAvailabilityStatus#UNAVAILABLE} — window not computed, no warm-up / gap /
 *       failure reason (no trade data, or the trade-flow group absent altogether).</li>
 *   <li>{@link FeatureAvailabilityStatus#AVAILABLE} — otherwise.</li>
 * </ol>
 * {@code sourceOrderBookTrusted} is book-specific quality and intentionally does not affect
 * trade-flow availability.
 */
public final class FeatureAvailabilityResolver {

    public static final String TRADE_FLOW_GROUP = "trade-flow";
    public static final String REASON_TRADE_HISTORY_GAP = "TRADE_HISTORY_GAP";

    public static final String CODE_CALCULATOR_FAILED = "TRADE_FLOW_CALCULATOR_FAILED";
    public static final String CODE_STALE_TRADES = "STALE_TRADES";
    public static final String CODE_TRADE_HISTORY_GAP = "TRADE_HISTORY_GAP";
    public static final String CODE_WINDOW_WARMING_UP = "WINDOW_WARMING_UP";
    public static final String CODE_WINDOW_NOT_COMPUTED = "WINDOW_NOT_COMPUTED";
    public static final String CODE_GROUP_ABSENT = "TRADE_FLOW_GROUP_ABSENT";
    public static final String CODE_WINDOW_COMPUTED = "WINDOW_COMPUTED";

    public TradeFlowAvailability resolveTradeFlow(MarketFeaturesSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<MarketHorizon, FeatureWindowAvailability> result = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            result.put(horizon, resolveTradeFlow(snapshot, horizon));
        }
        return new TradeFlowAvailability(result);
    }

    public FeatureWindowAvailability resolveTradeFlow(MarketFeaturesSnapshot snapshot, MarketHorizon horizon) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(horizon, "horizon");

        FeatureQuality quality = snapshot.quality();
        FeatureDiagnostics diagnostics = snapshot.diagnostics();
        TradeFlowFeature tradeFlow = snapshot.tradeFlow();

        // 1. FAILED
        if (diagnostics != null && diagnostics.failedFeatureGroups().contains(TRADE_FLOW_GROUP)) {
            return FeatureWindowAvailability.of(horizon, FeatureAvailabilityStatus.FAILED, CODE_CALCULATOR_FAILED);
        }

        // 2. UNTRUSTED (stale trades: all horizons)
        if (quality != null && quality.staleTrades()) {
            return FeatureWindowAvailability.of(horizon, FeatureAvailabilityStatus.UNTRUSTED, CODE_STALE_TRADES);
        }

        boolean computed = isComputed(tradeFlowWindowOf(tradeFlow, horizon), horizon);
        boolean historyGap = quality != null && quality.qualityReasons().contains(REASON_TRADE_HISTORY_GAP);
        boolean warmingUp = quality != null && quality.warmingUp();

        if (computed) {
            // 5. AVAILABLE — a computed window after a gap / during warm-up is complete by construction
            return FeatureWindowAvailability.of(horizon, FeatureAvailabilityStatus.AVAILABLE, CODE_WINDOW_COMPUTED);
        }
        // 2. UNTRUSTED (history gap: the uncovered windows are the ones spanning the gap)
        if (historyGap) {
            return FeatureWindowAvailability.of(horizon, FeatureAvailabilityStatus.UNTRUSTED, CODE_TRADE_HISTORY_GAP);
        }
        // 3. WARMING_UP
        if (warmingUp) {
            return FeatureWindowAvailability.of(horizon, FeatureAvailabilityStatus.WARMING_UP, CODE_WINDOW_WARMING_UP);
        }
        // 4. UNAVAILABLE
        return FeatureWindowAvailability.of(horizon, FeatureAvailabilityStatus.UNAVAILABLE,
                tradeFlow == null ? CODE_GROUP_ABSENT : CODE_WINDOW_NOT_COMPUTED);
    }

    /**
     * Whether the producer computed this window, per the presence markers described on the class.
     * Package-visible for tests.
     */
    static boolean isComputed(TradeFlowWindow window, MarketHorizon horizon) {
        if (window == null) {
            return false;
        }
        if (window.buyAggressiveVolume() != null
                || window.sellAggressiveVolume() != null
                || window.totalAggressiveVolume() != null
                || window.signedTradeFlow() != null
                || window.signedFlowImbalance() != null
                || window.tradeIntensity() != null
                || window.avgTradeSize() != null
                || window.vwap() != null) {
            return true;
        }
        if (hasNullableCounts(horizon)) {
            // null = not covered; any non-null counter (0 included) = measured window
            return window.tradeCount() != null
                    || window.validQtyTradeCount() != null
                    || window.aggressiveTradeCount() != null
                    || window.unknownSideCount() != null;
        }
        // non-nullable counters default to 0 on the wire: only a positive count proves computation
        return positive(window.tradeCount())
                || positive(window.validQtyTradeCount())
                || positive(window.aggressiveTradeCount())
                || positive(window.unknownSideCount());
    }

    /**
     * The trade-flow window of the given horizon, or {@code null} when the feature group is absent.
     * Delegates to the canonical {@link TradeFlowFeature#window(MarketHorizon)} selection.
     */
    static TradeFlowWindow tradeFlowWindowOf(TradeFlowFeature tradeFlow, MarketHorizon horizon) {
        Objects.requireNonNull(horizon, "horizon");
        return tradeFlow == null ? null : tradeFlow.window(horizon);
    }

    /**
     * Trade-flow wire contract per horizon: for {@code 1S}/{@code 5S} the counters are non-nullable Avro
     * {@code int}s with schema default {@code 0} (a zero count cannot by itself prove the window was
     * computed); for {@code 15S}/{@code 60S} they are {@code ["null","int"]} and {@code null} means
     * "window not covered / not computed" while {@code 0} is a genuinely measured empty window.
     */
    static boolean hasNullableCounts(MarketHorizon horizon) {
        return switch (Objects.requireNonNull(horizon, "horizon")) {
            case H1S, H5S -> false;
            case H15S, H60S -> true;
        };
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
