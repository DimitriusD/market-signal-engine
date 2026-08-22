package com.trading.marketsignalengine.application.domain.rule;

import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
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

/**
 * Phase-1 data-quality gate over MFS v2 quality. Two independent layers, both must pass for
 * {@link SignalType#DATA_TRADABLE}:
 * <ol>
 *   <li><b>per-source flags</b> ({@code syncStatus}, stale book/trades, incomplete book) — the legacy
 *       checks, kept as a backstop and for precise typed reasons;</li>
 *   <li><b>aggregate status</b> ({@code status}): {@code UNSAFE} / {@code NO_DATA} / missing →
 *       {@link SignalType#NO_TRADE_QUALITY_UNSAFE}; {@code DEGRADED} →
 *       {@link SignalType#NO_TRADE_QUALITY_DEGRADED}. DEGRADED is a hard block for the paper-trading
 *       period (path-to-paper-trading.md, decision 8.4): it covers soft staleness, incomplete book,
 *       warm-up (first 60s after the first trade), future events, failed calculators and trade-history
 *       gaps. Revisited once replay data shows how often and why DEGRADED occurs.</li>
 * </ol>
 * A missing status is never read as OK: an upstream writer that predates the field is treated as
 * unsafe (fail closed). Upstream {@code qualityReasons} are echoed verbatim in attributes so the
 * downstream consumer sees <em>why</em> without decoding flags.
 */
public class QualitySignalRule implements SignalRule {

    private static final String REASON_STATUS_MISSING = "QUALITY_STATUS_MISSING";

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

        // Layer 1: per-source flags.
        if (!quality.isTradable()) {
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
        }

        // Layer 2: aggregate status.
        FeatureQualityStatus status = quality.status();
        if (status == null) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_QUALITY_UNSAFE,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Aggregate quality status is missing",
                    qualityAttributes(quality, syncStatus, REASON_STATUS_MISSING)));
        } else if (status == FeatureQualityStatus.UNSAFE || status == FeatureQualityStatus.NO_DATA) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_QUALITY_UNSAFE,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Aggregate quality status is " + status.name(),
                    qualityAttributes(quality, syncStatus, "QUALITY_STATUS_" + status.name())));
        } else if (status == FeatureQualityStatus.DEGRADED) {
            signals.add(MarketSignal.riskOff(
                    SignalType.NO_TRADE_QUALITY_DEGRADED,
                    SignalStrength.STRONG,
                    BigDecimal.ONE,
                    "Aggregate quality status is DEGRADED (hard block during paper trading)",
                    qualityAttributes(quality, syncStatus, "QUALITY_STATUS_DEGRADED")));
        }

        if (signals.isEmpty()) {
            signals.add(MarketSignal.neutral(
                    SignalType.DATA_TRADABLE,
                    SignalStrength.NONE,
                    BigDecimal.ONE,
                    "Feature snapshot is tradable",
                    qualityAttributes(quality, syncStatus, "DATA_TRADABLE")));
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
            attributes.put("qualityStatus", quality.status() == null ? "MISSING" : quality.status().name());
            if (!quality.qualityReasons().isEmpty()) {
                attributes.put("qualityReasons", String.join(",", quality.qualityReasons()));
            }
            SignalAttributes.putBoolean(attributes, "staleOrderBookState", quality.staleOrderBookState());
            SignalAttributes.putBoolean(attributes, "staleTrades", quality.staleTrades());
            SignalAttributes.putBoolean(attributes, "incompleteBook", quality.incompleteBook());
            SignalAttributes.putBoolean(attributes, "warmingUp", quality.warmingUp());
            SignalAttributes.putBoolean(attributes, "futureEventDetected", quality.futureEventDetected());
            SignalAttributes.putBoolean(attributes, "sourceOrderBookTrusted", quality.sourceOrderBookTrusted());
            SignalAttributes.putIfPresent(attributes, "sourceOrderBookReason", quality.sourceOrderBookReason());
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
