/**
 * Market Interpretation V2 — the canonical internal domain model of the signal engine for
 * multi-horizon market interpretation (independent 1S / 5S / 15S / 60S assessments, explicit
 * eligibility, quality, evidence, cross-horizon alignment, market opportunity, full lineage and a
 * deterministic snapshot id).
 *
 * <p>Design rules of this package:
 * <ul>
 *   <li><b>Pure domain.</b> No Avro, Kafka, Spring, generated contract classes, infrastructure types,
 *       {@code Clock} or {@code Instant.now()}. Generated Avro classes are transport DTOs; a mapper
 *       (later stage) converts this model to them.</li>
 *   <li><b>Immutable and typed.</b> Every type is a record / final class with defensive copies,
 *       unmodifiable collections, no null elements, canonical ordering where an order exists
 *       ({@link com.trading.marketsignalengine.application.domain.model.MarketHorizon#canonicalOrder()},
 *       {@link com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension}
 *       declaration order) and fail-fast on duplicates in semantic collections.</li>
 *   <li><b>Invariants live in the model.</b> Every contract invariant of
 *       {@code MarketInterpretationSnapshotEvent} (trading-schemas 1.1.0) that Avro cannot express is
 *       enforced by constructors, factories or the aggregate — an object that exists is valid.</li>
 *   <li><b>Availability is not eligibility.</b> {@code FeatureAvailabilityStatus} (input side) says
 *       whether a feature window is there;
 *       {@link com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus}
 *       is a policy verdict on whether a whole horizon may be interpreted. An AVAILABLE input does not
 *       by itself make a horizon ELIGIBLE.</li>
 *   <li><b>UNKNOWN is not NEUTRAL.</b> A non-eligible horizon / non-available evidence is UNKNOWN with
 *       an absent strength, never NEUTRAL with a zero.</li>
 *   <li><b>No trading commands, no probabilities.</b> {@code MarketOpportunity} is an interpretation for
 *       a downstream strategy, not BUY/SELL; {@code EvidenceStrength} is heuristic evidence in [0,1],
 *       not a calibrated confidence or probability.</li>
 *   <li><b>No evaluation logic yet.</b> This package defines the language and the invariants of the V2
 *       engine; evaluators (flow / momentum / volatility / book, eligibility, quality, cross-horizon,
 *       opportunity) are a later stage.</li>
 * </ul>
 */
package com.trading.marketsignalengine.application.domain.interpretation;
