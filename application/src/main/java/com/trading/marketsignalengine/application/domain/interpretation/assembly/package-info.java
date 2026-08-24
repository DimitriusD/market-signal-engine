/**
 * Market interpretation snapshot assembly (Stage 9): the single safe pipeline from one validated
 * {@code MarketFeaturesSnapshot} + its Stage 3 {@code QualityAssessment} to one complete
 * {@code MarketInterpretationSnapshot} with a deterministic validity deadline. The assembler runs the
 * Stage 8 {@code MarketOpportunityEvaluator} itself, exactly once, so the snapshot/quality
 * consistency guard stays active and components of different feature snapshots can never be mixed;
 * the validity resolver is deliberately package-private. Timing semantics: {@code evaluatedAt} is
 * always the upstream feature evaluation tick ({@code sourceEvaluationAt}), and
 * {@code validUntil = sourceEvaluationAt + baseValidity(type, setup horizon) − fixed deductions} is
 * an exclusive absolute deadline; the remaining validity at assembly time is
 * {@code validUntil − qualityAssessment.timing.assessedAt}, so elapsed processing latency is never
 * deducted twice (it is already inside the feature age). A candidate whose deadline has passed at
 * assessment time is downgraded to NO_OPPORTUNITY before the snapshot is built — an expired
 * candidate never leaves this package as active. No Spring, Kafka, Avro, I/O, metrics, wall-clock
 * reads or production defaults; the same input and policy always assemble a value-equal snapshot
 * with the same deterministic id.
 */
package com.trading.marketsignalengine.application.domain.interpretation.assembly;
