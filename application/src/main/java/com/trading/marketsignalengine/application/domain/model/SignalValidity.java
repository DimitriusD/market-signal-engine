package com.trading.marketsignalengine.application.domain.model;

import java.time.Instant;

/**
 * Explicit validity window for a signal snapshot. {@code validUntil} is the absolute instant after
 * which the snapshot is stale; {@code ttlMs} is the relative TTL used to derive it.
 */
public record SignalValidity(
        long ttlMs,
        Instant validUntil) {
}
