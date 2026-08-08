package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
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

public class OrderBookSignalRule implements SignalRule {

    private static final BigDecimal MIN_IMBALANCE = new BigDecimal("-1");
    private static final BigDecimal MAX_IMBALANCE = BigDecimal.ONE;

    private static final String NEUTRAL_REASON = "neutralReason";
    private static final String SIGNAL_REASON = "signalReason";
    private static final String RISK_REASON = "riskReason";

    private static final String REASON_BOOK_MISSING = "BOOK_FEATURES_MISSING";
    private static final String REASON_TOP5_IMBALANCE_MISSING = "TOP5_IMBALANCE_MISSING";
    private static final String REASON_ABOVE_BULLISH_THRESHOLD = "TOP5_IMBALANCE_ABOVE_BULLISH_THRESHOLD";
    private static final String REASON_BELOW_BEARISH_THRESHOLD = "TOP5_IMBALANCE_BELOW_BEARISH_THRESHOLD";
    private static final String REASON_INSIDE_NEUTRAL_RANGE = "TOP5_IMBALANCE_INSIDE_NEUTRAL_RANGE";
    private static final String REASON_INVALID_LEVELS_USED = "LEVELS_USED_INVALID";
    private static final String REASON_TOP5_IMBALANCE_OUT_OF_RANGE = "TOP5_IMBALANCE_OUT_OF_RANGE";
    private static final String REASON_TOP1_IMBALANCE_OUT_OF_RANGE = "TOP1_IMBALANCE_OUT_OF_RANGE";

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        SignalConfiguration config = context.configuration();
        BookFeature book = features.book();

        if (book == null) {
            return List.of(MarketSignal.neutral(
                    SignalType.ORDER_BOOK_NEUTRAL,
                    SignalStrength.NONE,
                    new BigDecimal("0.5"),
                    "book features are missing",
                    bookAttributes(null, config, NEUTRAL_REASON, REASON_BOOK_MISSING)));
        }

        List<MarketSignal> invalid = invalidBookSignalIfNeeded(book, config);
        if (!invalid.isEmpty()) {
            return invalid;
        }

        if (book.top5Imbalance() == null) {
            return List.of(MarketSignal.neutral(
                    SignalType.ORDER_BOOK_NEUTRAL,
                    SignalStrength.NONE,
                    new BigDecimal("0.5"),
                    "top5Imbalance is missing",
                    bookAttributes(book, config, NEUTRAL_REASON, REASON_TOP5_IMBALANCE_MISSING)));
        }

        BigDecimal top5Imbalance = book.top5Imbalance();

        if (top5Imbalance.compareTo(config.buyBookImbalanceThreshold()) >= 0) {
            return List.of(MarketSignal.bullish(
                    SignalType.ORDER_BOOK_BULLISH,
                    SignalStrength.STRONG,
                    new BigDecimal("0.70"),
                    "top5Imbalance is above bullish threshold",
                    bookAttributes(book, config, SIGNAL_REASON, REASON_ABOVE_BULLISH_THRESHOLD)));
        }

        if (top5Imbalance.compareTo(config.sellBookImbalanceThreshold()) <= 0) {
            return List.of(MarketSignal.bearish(
                    SignalType.ORDER_BOOK_BEARISH,
                    SignalStrength.STRONG,
                    new BigDecimal("0.70"),
                    "top5Imbalance is below bearish threshold",
                    bookAttributes(book, config, SIGNAL_REASON, REASON_BELOW_BEARISH_THRESHOLD)));
        }

        return List.of(MarketSignal.neutral(
                SignalType.ORDER_BOOK_NEUTRAL,
                SignalStrength.NONE,
                new BigDecimal("0.5"),
                "top5Imbalance is within neutral range",
                bookAttributes(book, config, NEUTRAL_REASON, REASON_INSIDE_NEUTRAL_RANGE)));
    }

    /**
     * Rejects impossible order-book values before any directional verdict is computed: a non-positive
     * {@code levelsUsed} or a normalized imbalance outside [-1, 1] is an invalid feature value, not a
     * bullish/bearish book. They must yield {@link SignalType#NO_TRADE_INVALID_ORDER_BOOK} (RISK_OFF).
     */
    private static List<MarketSignal> invalidBookSignalIfNeeded(BookFeature book, SignalConfiguration config) {
        if (book.levelsUsed() <= 0) {
            return invalidBook("levelsUsed must be positive", book, config, REASON_INVALID_LEVELS_USED);
        }

        if (book.top5Imbalance() != null && outsideMinusOneToOne(book.top5Imbalance())) {
            return invalidBook("top5Imbalance is outside [-1, 1]", book, config, REASON_TOP5_IMBALANCE_OUT_OF_RANGE);
        }

        if (book.top1Imbalance() != null && outsideMinusOneToOne(book.top1Imbalance())) {
            return invalidBook("top1Imbalance is outside [-1, 1]", book, config, REASON_TOP1_IMBALANCE_OUT_OF_RANGE);
        }

        return List.of();
    }

    private static boolean outsideMinusOneToOne(BigDecimal value) {
        return value.compareTo(MIN_IMBALANCE) < 0 || value.compareTo(MAX_IMBALANCE) > 0;
    }

    private static List<MarketSignal> invalidBook(
            String reason,
            BookFeature book,
            SignalConfiguration config,
            String riskReason
    ) {
        return List.of(MarketSignal.riskOff(
                SignalType.NO_TRADE_INVALID_ORDER_BOOK,
                SignalStrength.STRONG,
                BigDecimal.ONE,
                reason,
                bookAttributes(book, config, RISK_REASON, riskReason)));
    }

    private static Map<String, String> bookAttributes(
            BookFeature book,
            SignalConfiguration config,
            String reasonKey,
            String reasonValue
    ) {
        Map<String, String> attributes = new LinkedHashMap<>();

        attributes.put("buyBookImbalanceThreshold", config.buyBookImbalanceThreshold().toPlainString());
        attributes.put("sellBookImbalanceThreshold", config.sellBookImbalanceThreshold().toPlainString());

        if (reasonKey != null && reasonValue != null) {
            attributes.put(reasonKey, reasonValue);
        }

        if (book != null) {
            SignalAttributes.putInt(attributes, "levelsUsed", book.levelsUsed());
            SignalAttributes.putIfPresent(attributes, "top1Imbalance", book.top1Imbalance());
            SignalAttributes.putIfPresent(attributes, "top5Imbalance", book.top5Imbalance());
            SignalAttributes.putIfPresent(attributes, "bidLiquidityTop5", book.bidLiquidityTop5());
            SignalAttributes.putIfPresent(attributes, "askLiquidityTop5", book.askLiquidityTop5());
            SignalAttributes.putIfPresent(attributes, "bestBidGapTicks", book.bestBidGapTicks());
            SignalAttributes.putIfPresent(attributes, "bestAskGapTicks", book.bestAskGapTicks());
        }

        return attributes;
    }
}
