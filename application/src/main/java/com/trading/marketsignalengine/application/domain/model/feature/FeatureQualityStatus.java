package com.trading.marketsignalengine.application.domain.model.feature;

/**
 * Aggregate feature-quality status published by MFS v2 (mirrors
 * {@code com.trading.contracts.feature.FeatureQualityStatus}). Upstream semantics:
 * {@code NO_DATA} — neither book nor trades; {@code UNSAFE} — book missing / untrusted /
 * out-of-sync / hard-stale; {@code DEGRADED} — soft staleness, incomplete book, warm-up, future
 * event, failed calculator, trade-history gap; {@code OK} — everything else.
 */
public enum FeatureQualityStatus {
    OK,
    DEGRADED,
    UNSAFE,
    NO_DATA
}
