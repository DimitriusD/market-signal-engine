package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSetup;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.SignalValidity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SignalAggregator {

    private final SetupResolver setupResolver;
    private final SignalValidityResolver validityResolver;

    public SignalAggregator() {
        this(new SetupResolver(), new SignalValidityResolver());
    }

    public SignalAggregator(SetupResolver setupResolver, SignalValidityResolver validityResolver) {
        this.setupResolver = setupResolver;
        this.validityResolver = validityResolver;
    }

    public MarketSignalSnapshot aggregate(SignalEvaluationContext context, List<MarketSignal> signals) {
        MarketFeaturesSnapshot features = context.features();
        List<MarketSignal> inputSignals = List.copyOf(signals);

        DirectionalReduction reduction = DirectionalReduction.from(inputSignals);
        RiskLevel riskLevel = resolveRiskLevel(reduction);
        List<MarketSignal> allSignals = withDiagnostics(inputSignals, reduction);

        MarketSetup setup = setupResolver.resolve(allSignals, riskLevel);
        SignalValidity validity = validityResolver.resolve(context, riskLevel, setup);

        return new MarketSignalSnapshot(
                deterministicSignalSnapshotId(features.snapshotId(), context.configuration().signalSetVersion()),
                features.snapshotId(),
                features.exchange(),
                features.marketType(),
                features.base(),
                features.quote(),
                features.symbol(),
                features.instrumentId(),
                features.eventTime(),
                context.evaluatedAt(),
                validity.validUntil(),
                validity.ttlMs(),
                features.featureSetVersion(),
                context.configuration().signalSetVersion(),
                reduction.bias(),
                reduction.score(),
                riskLevel,
                setup,
                allSignals);
    }

    /**
     * Derives the signal snapshot id deterministically from the source feature snapshot id and the
     * signal-set version (a name-based, RFC&nbsp;4122 v3 UUID). Re-processing the same feature
     * snapshot under the same signal logic therefore yields the same id, so a duplicate input after a
     * retry or crash dedupes downstream instead of looking like a brand-new signal event. The id is
     * keyed on {@code signalSetVersion} — not the full configuration — so any change to the signal
     * rules or thresholds must be accompanied by a version bump, exactly as upstream treats
     * {@code featureSetVersion}.
     */
    private static String deterministicSignalSnapshotId(String sourceFeatureSnapshotId, String signalSetVersion) {
        String key = sourceFeatureSnapshotId + "|" + signalSetVersion;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private RiskLevel resolveRiskLevel(DirectionalReduction reduction) {
        if (reduction.riskOff()) {
            return RiskLevel.NO_TRADE;
        }
        if (reduction.conflict()) {
            return RiskLevel.ELEVATED;
        }
        return RiskLevel.NORMAL;
    }

    /**
     * Appends MARKET_MIXED as a diagnostic when, and only when, the reduction resolved to MIXED.
     * Emitting it here (rather than in the composite rule) keeps it derived from the same reduction
     * that produced the bias, so the signal and {@code marketBias} can never disagree.
     */
    private List<MarketSignal> withDiagnostics(List<MarketSignal> signals, DirectionalReduction reduction) {
        if (reduction.bias() != MarketBias.MIXED) {
            return signals;
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("diagnosticReason", "BULLISH_AND_BEARISH_BASE_SIGNALS_CONFLICT");
        attributes.put("hasBullishBase", Boolean.toString(reduction.hasBullishBase()));
        attributes.put("hasBearishBase", Boolean.toString(reduction.hasBearishBase()));
        attributes.put("marketBiasScore", reduction.score().toPlainString());
        attributes.put("directionalThreshold", DirectionalReduction.DIRECTIONAL_THRESHOLD.toPlainString());

        List<MarketSignal> withDiagnostics = new ArrayList<>(signals);
        withDiagnostics.add(MarketSignal.neutral(
                SignalType.MARKET_MIXED,
                SignalStrength.MODERATE,
                new BigDecimal("0.60"),
                "Bullish and bearish base signals conflict without a dominant direction",
                attributes));
        return List.copyOf(withDiagnostics);
    }
}
