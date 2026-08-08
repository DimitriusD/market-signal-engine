package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tradability-risk gate on the quoted spread. Missing spread data is never read as a wide spread: a
 * missing BBO or a missing {@code spreadBps} emits a RISK_OFF {@link SignalType#NO_TRADE_SPREAD_MISSING}
 * so the engine short-circuits, while only a genuinely-computed spread above threshold emits
 * {@link SignalType#SPREAD_TOO_WIDE}.
 */
public class SpreadSignalRule implements SignalRule {

    private static final String RISK_REASON = "riskReason";
    private static final String SPREAD_BPS = "spreadBps";
    private static final String MAX_SPREAD_BPS = "maxSpreadBps";
    private static final String SPREAD_ABS = "spreadAbs";
    private static final String BEST_BID_PRICE = "bestBidPrice";
    private static final String BEST_ASK_PRICE = "bestAskPrice";
    private static final String MID_PRICE = "midPrice";
    private static final String MICROPRICE_TOP1 = "micropriceTop1";
    private static final String MICROPRICE_OFFSET_BPS = "micropriceOffsetBps";
    private static final String BEST_BID_QTY = "bestBidQty";
    private static final String BEST_ASK_QTY = "bestAskQty";

    private static final String REASON_BBO_MISSING = "BBO_MISSING";
    private static final String REASON_SPREAD_BPS_MISSING = "SPREAD_BPS_MISSING";
    private static final String REASON_ABOVE_THRESHOLD = "SPREAD_BPS_ABOVE_THRESHOLD";

    private static final String REASON_INVALID_BID_PRICE = "BEST_BID_PRICE_INVALID";
    private static final String REASON_INVALID_ASK_PRICE = "BEST_ASK_PRICE_INVALID";
    private static final String REASON_CROSSED_BBO = "CROSSED_BBO";
    private static final String REASON_NEGATIVE_SPREAD_BPS = "NEGATIVE_SPREAD_BPS";
    private static final String REASON_NEGATIVE_SPREAD_ABS = "NEGATIVE_SPREAD_ABS";

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        SignalConfiguration config = context.configuration();
        BboFeature bbo = features.bbo();

        if (bbo == null) {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put(RISK_REASON, REASON_BBO_MISSING);
            attributes.put(MAX_SPREAD_BPS, config.maxSpreadBps().toPlainString());
            return List.of(MarketSignal.riskOff(
                    SignalType.NO_TRADE_SPREAD_MISSING,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "BBO features are missing",
                    attributes));
        }

        List<MarketSignal> invalid = invalidBboSignalIfNeeded(bbo, config);
        if (!invalid.isEmpty()) {
            return invalid;
        }

        if (bbo.spreadBps() == null) {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put(RISK_REASON, REASON_SPREAD_BPS_MISSING);
            attributes.put(MAX_SPREAD_BPS, config.maxSpreadBps().toPlainString());
            putIfPresent(attributes, BEST_BID_PRICE, bbo.bestBidPrice());
            putIfPresent(attributes, BEST_ASK_PRICE, bbo.bestAskPrice());
            putIfPresent(attributes, SPREAD_ABS, bbo.spreadAbs());
            putIfPresent(attributes, MID_PRICE, bbo.midPrice());
            putIfPresent(attributes, MICROPRICE_TOP1, bbo.micropriceTop1());
            putIfPresent(attributes, MICROPRICE_OFFSET_BPS, bbo.micropriceOffsetBps());
            putIfPresent(attributes, BEST_BID_QTY, bbo.bestBidQty());
            putIfPresent(attributes, BEST_ASK_QTY, bbo.bestAskQty());
            return List.of(MarketSignal.riskOff(
                    SignalType.NO_TRADE_SPREAD_MISSING,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "spreadBps is missing",
                    attributes));
        }

        if (bbo.spreadBps().compareTo(config.maxSpreadBps()) <= 0) {
            return List.of(MarketSignal.neutral(
                    SignalType.SPREAD_ACCEPTABLE,
                    SignalStrength.NONE,
                    BigDecimal.ONE,
                    "Spread is within configured threshold",
                    spreadAttributes(bbo, config, null)));
        }

        return List.of(MarketSignal.riskOff(
                SignalType.SPREAD_TOO_WIDE,
                SignalStrength.STRONG,
                BigDecimal.ONE,
                "Spread is above configured threshold",
                spreadAttributes(bbo, config, REASON_ABOVE_THRESHOLD)));
    }

    /**
     * Rejects impossible BBO geometry before any spread verdict is computed: a non-positive bid/ask, a
     * crossed book (bid &gt; ask), or a negative spread are invalid feature values, not a wide spread.
     * They must yield {@link SignalType#NO_TRADE_INVALID_BBO} (RISK_OFF) rather than SPREAD_ACCEPTABLE.
     */
    private static List<MarketSignal> invalidBboSignalIfNeeded(BboFeature bbo, SignalConfiguration config) {
        if (bbo.bestBidPrice() != null && bbo.bestBidPrice().signum() <= 0) {
            return invalidBbo("bestBidPrice must be positive", bbo, config, REASON_INVALID_BID_PRICE);
        }
        if (bbo.bestAskPrice() != null && bbo.bestAskPrice().signum() <= 0) {
            return invalidBbo("bestAskPrice must be positive", bbo, config, REASON_INVALID_ASK_PRICE);
        }
        if (bbo.bestBidPrice() != null
                && bbo.bestAskPrice() != null
                && bbo.bestBidPrice().compareTo(bbo.bestAskPrice()) > 0) {
            return invalidBbo("bestBidPrice is greater than bestAskPrice", bbo, config, REASON_CROSSED_BBO);
        }
        if (bbo.spreadBps() != null && bbo.spreadBps().signum() < 0) {
            return invalidBbo("spreadBps is negative", bbo, config, REASON_NEGATIVE_SPREAD_BPS);
        }
        if (bbo.spreadAbs() != null && bbo.spreadAbs().signum() < 0) {
            return invalidBbo("spreadAbs is negative", bbo, config, REASON_NEGATIVE_SPREAD_ABS);
        }
        return List.of();
    }

    private static List<MarketSignal> invalidBbo(
            String reason,
            BboFeature bbo,
            SignalConfiguration config,
            String riskReason
    ) {
        return List.of(MarketSignal.riskOff(
                SignalType.NO_TRADE_INVALID_BBO,
                SignalStrength.STRONG,
                BigDecimal.ONE,
                reason,
                spreadAttributes(bbo, config, riskReason)));
    }

    private static Map<String, String> spreadAttributes(BboFeature bbo,
                                                        SignalConfiguration config,
                                                        String riskReason) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (riskReason != null) {
            attributes.put(RISK_REASON, riskReason);
        }
        // Null-safe: an invalid BBO (crossed book, non-positive bid/ask) can reach here without a
        // computed spreadBps. Acceptable/too-wide paths always pass a non-null spreadBps, so this stays
        // present for them.
        putIfPresent(attributes, SPREAD_BPS, bbo.spreadBps());
        attributes.put(MAX_SPREAD_BPS, config.maxSpreadBps().toPlainString());
        putIfPresent(attributes, SPREAD_ABS, bbo.spreadAbs());
        putIfPresent(attributes, BEST_BID_PRICE, bbo.bestBidPrice());
        putIfPresent(attributes, BEST_ASK_PRICE, bbo.bestAskPrice());
        putIfPresent(attributes, MID_PRICE, bbo.midPrice());
        putIfPresent(attributes, MICROPRICE_TOP1, bbo.micropriceTop1());
        putIfPresent(attributes, MICROPRICE_OFFSET_BPS, bbo.micropriceOffsetBps());
        putIfPresent(attributes, BEST_BID_QTY, bbo.bestBidQty());
        putIfPresent(attributes, BEST_ASK_QTY, bbo.bestAskQty());
        return attributes;
    }

    private static void putIfPresent(Map<String, String> attributes, String key, BigDecimal value) {
        if (value != null) {
            attributes.put(key, value.toPlainString());
        }
    }
}
