package com.trading.marketsignalengine.application.domain.validation;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;

/**
 * Structural-contract guard on the inbound feature snapshot. This is a programming/data-pipeline
 * concern (missing identity, missing timestamps), not a market-quality concern: such events must
 * fail-fast to the DLT rather than produce a signal snapshot with a {@code "null|version"} id or an
 * empty instrumentId. Semantically-invalid feature values (crossed BBO, out-of-range imbalance, ...)
 * are a different problem handled inside the rules as RISK_OFF no-trade signals.
 */
public class MarketFeaturesSnapshotValidator {

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
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidMarketFeaturesSnapshotException(fieldName + " must not be blank");
        }
    }
}
