package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalDirection;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Single directional reduction of a signal set. {@code bias} and {@code score} are two projections
 * of one computation, so they can never contradict. The following invariants hold by construction:
 * <ul>
 *   <li>{@code bias == BULLISH} implies {@code score > 0}</li>
 *   <li>{@code bias == BEARISH} implies {@code score < 0}</li>
 *   <li>{@code bias == NEUTRAL} implies {@code abs(score) < threshold}</li>
 *   <li>{@code bias == RISK_OFF} implies {@code score == 0}</li>
 *   <li>{@code bias == MIXED} implies {@code abs(score) < threshold} and a bull/bear conflict</li>
 * </ul>
 * Only base directional signals feed the score; composite/setup signals (LONG_SETUP_FORMING,
 * SHORT_SETUP_FORMING, MARKET_MIXED) are deliberately excluded to avoid double counting.
 */
public record DirectionalReduction(
        BigDecimal score,
        boolean hasBullishBase,
        boolean hasBearishBase,
        boolean riskOff,
        MarketBias bias) {

    static final BigDecimal DIRECTIONAL_THRESHOLD = new BigDecimal("0.35");

    private static final BigDecimal SIGNAL_WEIGHT = new BigDecimal("0.25");
    private static final BigDecimal MIN_SCORE = new BigDecimal("-1");
    private static final BigDecimal MAX_SCORE = BigDecimal.ONE;

    /**
     * Base directional signals that contribute to the score and conflict detection. REGIME is
     * intentionally excluded: {@code lastTradeDistanceToMidBps} is microstructure noise, not a trend.
     */
    private static final Set<SignalType> DIRECTIONAL_BASE_TYPES = EnumSet.of(
            SignalType.BUY_PRESSURE,
            SignalType.SELL_PRESSURE,
            SignalType.ORDER_BOOK_BULLISH,
            SignalType.ORDER_BOOK_BEARISH);

    public static DirectionalReduction from(List<MarketSignal> signals) {
        boolean riskOff = signals.stream()
                .anyMatch(signal -> signal.direction() == SignalDirection.RISK_OFF);
        if (riskOff) {
            return new DirectionalReduction(BigDecimal.ZERO, false, false, true, MarketBias.RISK_OFF);
        }

        BigDecimal score = BigDecimal.ZERO;
        boolean hasBullishBase = false;
        boolean hasBearishBase = false;
        for (MarketSignal signal : signals) {
            if (!DIRECTIONAL_BASE_TYPES.contains(signal.type())) {
                continue;
            }
            if (signal.direction() == SignalDirection.BULLISH) {
                score = score.add(SIGNAL_WEIGHT);
                hasBullishBase = true;
            } else if (signal.direction() == SignalDirection.BEARISH) {
                score = score.subtract(SIGNAL_WEIGHT);
                hasBearishBase = true;
            }
        }

        score = clamp(score, MIN_SCORE, MAX_SCORE);
        boolean conflict = hasBullishBase && hasBearishBase;
        MarketBias bias = resolveBias(score, conflict);
        return new DirectionalReduction(score, hasBullishBase, hasBearishBase, false, bias);
    }

    public boolean conflict() {
        return hasBullishBase && hasBearishBase;
    }

    private static MarketBias resolveBias(BigDecimal score, boolean conflict) {
        if (score.compareTo(DIRECTIONAL_THRESHOLD) >= 0) {
            return MarketBias.BULLISH;
        }
        if (score.compareTo(DIRECTIONAL_THRESHOLD.negate()) <= 0) {
            return MarketBias.BEARISH;
        }
        if (conflict) {
            return MarketBias.MIXED;
        }
        return MarketBias.NEUTRAL;
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }
}
