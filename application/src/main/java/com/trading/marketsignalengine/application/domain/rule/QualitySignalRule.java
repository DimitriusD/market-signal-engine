package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QualitySignalRule implements SignalRule {

    @Override
    public List<MarketSignal> evaluate(SignalEvaluationContext context) {
        MarketFeaturesSnapshot features = context.features();
        FeatureQuality quality = features.quality();
        List<MarketSignal> signals = new ArrayList<>();

        if (quality == null) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_QUALITY_MISSING,
                    SignalStrength.EXTREME,
                    BigDecimal.ONE,
                    "Feature quality is missing",
                    qualityAttributes(null, SyncStatus.UNKNOWN, "QUALITY_MISSING")));
            return signals;
        }

        SyncStatus syncStatus = quality.syncStatus() != null ? quality.syncStatus() : SyncStatus.UNKNOWN;

        if (quality.isTradable()) {
            signals.add(MarketSignal.neutral(
                    SignalType.DATA_TRADABLE,
                    SignalStrength.NONE,
                    BigDecimal.ONE,
                    "Feature snapshot is tradable",
                    qualityAttributes(quality, syncStatus, "DATA_TRADABLE")));
            return signals;
        }

        if (syncStatus == SyncStatus.OUT_OF_SYNC || syncStatus == SyncStatus.UNKNOWN) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_OUT_OF_SYNC,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Feature snapshot is out of sync",
                    qualityAttributes(quality, syncStatus, "OUT_OF_SYNC_OR_UNKNOWN")));
        }

        if (syncStatus == SyncStatus.RECOVERING) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_RECOVERING_BOOK,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Order book is recovering",
                    qualityAttributes(quality, syncStatus, "RECOVERING_BOOK")));
        }

        if (syncStatus == SyncStatus.STALE || quality.staleOrderBookState()) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_STALE_BOOK,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    staleBookReason(syncStatus, quality),
                    qualityAttributes(quality, syncStatus, "STALE_BOOK")));
        }

        if (quality.staleTrades()) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_STALE_TRADES,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Trade data is stale",
                    qualityAttributes(quality, syncStatus, "STALE_TRADES")));
        }

        if (quality.incompleteBook()) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_INCOMPLETE_BOOK,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Order book is incomplete",
                    qualityAttributes(quality, syncStatus, "INCOMPLETE_BOOK")));
        }

        return signals;
    }

    private static Map<String, String> qualityAttributes(FeatureQuality quality, SyncStatus syncStatus, String reason) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("qualityReason", reason);

        if (syncStatus != null) {
            attributes.put("syncStatus", syncStatus.name());
        }

        if (quality != null) {
            SignalAttributes.putBoolean(attributes, "staleOrderBookState", quality.staleOrderBookState());
            SignalAttributes.putBoolean(attributes, "staleTrades", quality.staleTrades());
            SignalAttributes.putBoolean(attributes, "incompleteBook", quality.incompleteBook());
            SignalAttributes.putIfPresent(attributes, "orderBookStateAgeMs", quality.orderBookStateAgeMs());
            SignalAttributes.putIfPresent(attributes, "tradeAgeMs", quality.tradeAgeMs());
        }

        return attributes;
    }

    private static String staleBookReason(SyncStatus syncStatus, FeatureQuality quality) {
        boolean staleSync = syncStatus == SyncStatus.STALE;
        boolean staleState = quality.staleOrderBookState();
        if (staleSync && staleState) {
            return "Order book sync status and state are stale";
        }
        return staleSync ? "Order book sync status is stale" : "Order book state is stale";
    }
}
