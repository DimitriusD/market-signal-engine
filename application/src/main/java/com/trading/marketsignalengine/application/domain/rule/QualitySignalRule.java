package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class QualitySignalRule implements SignalRule {

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        FeatureQuality quality = features.quality();
        List<MarketSignal> signals = new ArrayList<>();

        if (quality == null) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_CONDITION,
                    SignalStrength.EXTREME,
                    BigDecimal.ONE,
                    "Feature quality is missing",
                    null));
            return signals;
        }

        if (quality.isTradable()) {
            signals.add(MarketSignal.neutral(
                    SignalType.DATA_TRADABLE,
                    SignalStrength.NONE,
                    BigDecimal.ONE,
                    "Feature snapshot is tradable",
                    null));
            return signals;
        }

        SyncStatus syncStatus = quality.syncStatus() != null ? quality.syncStatus() : SyncStatus.UNKNOWN;

        if (syncStatus == SyncStatus.OUT_OF_SYNC || syncStatus == SyncStatus.UNKNOWN) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_OUT_OF_SYNC,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Feature snapshot is out of sync",
                    null));
        }

        if (syncStatus == SyncStatus.RECOVERING) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_RECOVERING_BOOK,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Order book is recovering",
                    null));
        }

        if (syncStatus == SyncStatus.STALE) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_STALE_BOOK,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Order book sync status is stale",
                    null));
        }

        if (quality.staleBbo()) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_STALE_BBO,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "BBO data is stale",
                    null));
        }

        if (quality.staleBook()) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_STALE_BOOK,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Order book data is stale",
                    null));
        }

        if (quality.staleTrades()) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_STALE_TRADES,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Trade data is stale",
                    null));
        }

        if (quality.incompleteBook()) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_INCOMPLETE_BOOK,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Order book is incomplete",
                    null));
        }

        return signals;
    }
}
