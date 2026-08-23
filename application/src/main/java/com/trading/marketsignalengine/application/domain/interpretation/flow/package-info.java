/**
 * Flow evidence layer of the Market Interpretation V2 engine (roadmap §8.3 / §11.2, "Етап 4"): the
 * pure, deterministic step that turns one validated {@code MarketFeaturesSnapshot}, its Stage 3
 * {@code QualityAssessment} and an explicit, versioned
 * {@link com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentPolicy}
 * into exactly one {@code FLOW} {@code EvidenceAssessment} per {@code MarketHorizon}
 * ({@code 1S, 5S, 15S, 60S}), packaged as
 * {@link com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessments}.
 *
 * <pre>
 *   MarketFeaturesSnapshot + QualityAssessment + FlowAssessmentPolicy
 *     → FlowAssessmentEvaluator   (per horizon: eligibility → window → missing → invalid → activity
 *                                  → unknown-side → direction)
 *     → FlowAssessments { 1S, 5S, 15S, 60S → EvidenceAssessment(FLOW) }
 * </pre>
 *
 * <p>Rules of this package:
 * <ul>
 *   <li><b>Pure.</b> No Spring, Kafka, Avro / generated schemas, infrastructure, {@code Clock},
 *       {@code Instant.now()} or metrics. Same input + policy ⇒ value-equal result.</li>
 *   <li><b>Eligibility first.</b> A horizon that is not ELIGIBLE (Stage 3) is projected to a
 *       non-AVAILABLE FLOW evidence with its eligibility reasons kept verbatim; its feature values are
 *       never read, so warm-up, missing, untrusted or failed input never becomes NEUTRAL.</li>
 *   <li><b>Four distinct non-directional states.</b> Missing input ({@code UNAVAILABLE}), invalid /
 *       low-quality input ({@code UNTRUSTED}), insufficient activity ({@code AVAILABLE} + direction
 *       {@code UNKNOWN}, no strength) and NEUTRAL ({@code AVAILABLE}, strength {@code 0}) are never
 *       collapsed into one another.</li>
 *   <li><b>No hidden thresholds.</b> Every threshold and minimum comes from the horizon-specific,
 *       versioned policy; the evaluator carries no defaults and no production values.</li>
 *   <li><b>Heuristic, not probabilistic.</b> {@code EvidenceStrength = |signedFlowImbalance|} is an
 *       uncalibrated heuristic reading, not a probability or confidence.</li>
 *   <li><b>Per horizon only.</b> No {@code MIXED}, no alignment / pullback / reversal, no regime, no
 *       opportunity: those belong to the cross-horizon and opportunity layers (later stages).</li>
 * </ul>
 */
package com.trading.marketsignalengine.application.domain.interpretation.flow;
