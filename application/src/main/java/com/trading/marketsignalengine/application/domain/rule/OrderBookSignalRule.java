package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.List;

public class OrderBookSignalRule implements SignalRule {

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        SignalConfiguration config = context.configuration();
        BookFeature book = features.book();

        if (book == null || book.top5Imbalance() == null) {
            return List.of(MarketSignal.neutral(
                    SignalType.ORDER_BOOK_NEUTRAL,
                    SignalStrength.NONE,
                    new BigDecimal("0.5"),
                    "top5Imbalance is missing",
                    null));
        }

        BigDecimal top5Imbalance = book.top5Imbalance();

        if (top5Imbalance.compareTo(config.buyBookImbalanceThreshold()) >= 0) {
            return List.of(MarketSignal.bullish(
                    SignalType.ORDER_BOOK_BULLISH,
                    SignalStrength.STRONG,
                    new BigDecimal("0.70"),
                    "top5Imbalance is above bullish threshold",
                    null));
        }

        if (top5Imbalance.compareTo(config.sellBookImbalanceThreshold()) <= 0) {
            return List.of(MarketSignal.bearish(
                    SignalType.ORDER_BOOK_BEARISH,
                    SignalStrength.STRONG,
                    new BigDecimal("0.70"),
                    "top5Imbalance is below bearish threshold",
                    null));
        }

        return List.of(MarketSignal.neutral(
                SignalType.ORDER_BOOK_NEUTRAL,
                SignalStrength.NONE,
                new BigDecimal("0.5"),
                "top5Imbalance is within neutral range",
                null));
    }
}
