package com.trading.marketsignalengine.application.domain.validation;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.util.Set;
import java.util.TreeSet;

/**
 * Structural-contract guard on the inbound feature snapshot. This is a programming/data-pipeline
 * concern (missing identity, missing timestamps, unsupported contract version), not a market-quality
 * concern: such events must fail-fast to the DLT rather than produce a signal snapshot with a
 * {@code "null|version"} id, an empty instrumentId, or rules silently reading a contract they were
 * not written for. Semantically-invalid feature values (crossed BBO, out-of-range imbalance, ...)
 * are a different problem handled inside the rules as RISK_OFF no-trade signals.
 *
 * <p>Compatibility: {@code featureSetVersion} must be in the configured allowlist. The engine's rules
 * and thresholds are written against a specific upstream feature semantics; an unknown version is
 * rejected (fail closed) instead of being interpreted on assumptions. Widening the allowlist is a
 * deliberate configuration change, visible in deployment config.
 */
public class MarketFeaturesSnapshotValidator {

    private final Set<String> supportedFeatureSetVersions;

    public MarketFeaturesSnapshotValidator(Set<String> supportedFeatureSetVersions) {
        if (supportedFeatureSetVersions == null || supportedFeatureSetVersions.isEmpty()) {
            throw new IllegalArgumentException("supportedFeatureSetVersions must not be empty");
        }
        Set<String> normalized = new TreeSet<>();
        for (String version : supportedFeatureSetVersions) {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("supportedFeatureSetVersions must not contain blank entries");
            }
            normalized.add(version.trim());
        }
        this.supportedFeatureSetVersions = Set.copyOf(normalized);
    }

    public Set<String> supportedFeatureSetVersions() {
        return supportedFeatureSetVersions;
    }

    public void validate(MarketFeaturesSnapshot snapshot) {
        if (snapshot == null) {
            throw new InvalidMarketFeaturesSnapshotException("MarketFeaturesSnapshot must not be null");
        }
        requireNonBlank(snapshot.snapshotId(), "snapshotId");
        requireNonBlank(snapshot.instrumentId(), "instrumentId");
        requireNonBlank(snapshot.featureSetVersion(), "featureSetVersion");

        if (snapshot.eventTime() == null) {
            throw new InvalidMarketFeaturesSnapshotException("eventTime must not be null");
        }

        if (!supportedFeatureSetVersions.contains(snapshot.featureSetVersion())) {
            throw new InvalidMarketFeaturesSnapshotException(
                    "unsupported featureSetVersion '" + snapshot.featureSetVersion()
                            + "' (supported: " + new TreeSet<>(supportedFeatureSetVersions) + ")");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidMarketFeaturesSnapshotException(fieldName + " must not be blank");
        }
    }
}
