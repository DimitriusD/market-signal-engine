/**
 * Momentum V1 evidence: pure, deterministic per-horizon classification of the signed short-term
 * price move ({@code RegimeFeature.priceChangeBps5s/15s/60s}) into an independent {@code MOMENTUM}
 * {@code EvidenceAssessment} per {@code MarketHorizon}. MFS v2 publishes no 1S price change, so the
 * 1S horizon is explicitly UNAVAILABLE ({@code MOMENTUM_NOT_SCOPED_TO_HORIZON}) — never approximated
 * by the 5S value. No Spring, Kafka, Avro, I/O, metrics, clock or production defaults.
 */
package com.trading.marketsignalengine.application.domain.interpretation.momentum;
