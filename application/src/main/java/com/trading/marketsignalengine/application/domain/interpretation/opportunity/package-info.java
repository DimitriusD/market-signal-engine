/**
 * Deterministic market opportunity resolution (Stage 8): reduces one snapshot's
 * {@code QualityAssessment} + {@code CrossHorizonEvaluation} into one {@code MarketOpportunity}.
 * A CANDIDATE is an identified market setup for a downstream strategy/execution evaluation —
 * <b>not</b> a trade command: CANDIDATE LONG ≠ BUY and CANDIDATE SHORT ≠ SELL; there is no order,
 * position, quantity, price, stop, expected return, probability or confidence anywhere here. The
 * first version supports only MOMENTUM_CONTINUATION (LONG/SHORT), NO_OPPORTUNITY and BLOCKED —
 * SHORT_TERM_REVERSAL is deliberately not produced. The quality gate is absolute: a snapshot that is
 * not {@code eligibleForTrading} is always BLOCKED, regardless of the market interpretation. Only
 * full structural alignment ({@code ALIGNED_BULLISH}/{@code ALIGNED_BEARISH}) may become a
 * candidate, and only with independent evidence confirmation (H15S MOMENTUM persistence + H5S FLOW
 * trigger), no adverse Book, a real aggregate strength and a continuation-compatible regime. The
 * single safe public entry point is {@code MarketOpportunityEvaluator.evaluate(snapshot,
 * qualityAssessment, policy)} — it derives the cross-horizon evaluation itself, exactly once, so the
 * snapshot/quality consistency guard stays active; the resolver is deliberately package-private. No
 * Spring, Kafka, Avro, I/O, metrics, clock or production defaults.
 */
package com.trading.marketsignalengine.application.domain.interpretation.opportunity;
