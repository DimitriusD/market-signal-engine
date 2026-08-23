/**
 * Volatility V1 evidence: pure, deterministic per-horizon classification of the realized volatility
 * ({@code RegimeFeature.realizedVolatilityBps1s/5s/15s/60s}) into a typed
 * {@code VolatilityAssessment} ({@code VolatilityLevel} + {@code VOLATILITY}
 * {@code EvidenceAssessment}) per {@code MarketHorizon}. Volatility is a regime, not a directional
 * vote: AVAILABLE evidence always reads direction UNKNOWN with no strength, and HIGH / EXTREME
 * levels neither block a horizon nor create a NO_TRADE here — they are context for the future
 * regime / opportunity layer. No Spring, Kafka, Avro, I/O, metrics, clock or production defaults.
 */
package com.trading.marketsignalengine.application.domain.interpretation.volatility;
