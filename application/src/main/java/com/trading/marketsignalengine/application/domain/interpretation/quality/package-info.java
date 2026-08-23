/**
 * Quality layer of the Market Interpretation V2 engine (roadmap §8.2): the pure, deterministic
 * step that turns one <em>validated</em> {@code MarketFeaturesSnapshot} into a typed
 * {@link com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment} —
 * overall {@code InterpretationQuality}, a {@code TimingAssessment}, the typed failed feature groups
 * and exactly one {@code HorizonEligibility} per {@code MarketHorizon} ({@code 1S, 5S, 15S, 60S}).
 *
 * <p>Pipeline (every step pure; same snapshot + {@code assessedAt} + policy ⇒ same result):
 * <pre>
 *   MarketFeaturesSnapshot + explicit assessedAt + QualityEligibilityPolicy
 *     → FeatureAvailabilityResolver      (input-side fact: is the trade-flow window there)
 *     → HorizonEligibilityResolver       (policy verdict per horizon)
 *     → TimingAssessmentResolver         (feature age / processing latency vs. policy)
 *     → QualityAssessmentResolver        (global hard gates + overall quality)
 *     → QualityAssessment
 * </pre>
 *
 * <p>Rules of this package:
 * <ul>
 *   <li><b>Pure.</b> No Spring, Kafka, Avro / generated schemas, infrastructure, {@code Clock},
 *       {@code Instant.now()} or metrics. The assessment instant is an explicit input.</li>
 *   <li><b>Two instants are never confused.</b> {@code snapshot.evaluationTs} is the market as-of
 *       instant MFS computed the windows with; {@code assessedAt} is the instant the signal engine
 *       judges freshness at (live: injected clock — later stage; replay: a recorded / fixed instant).</li>
 *   <li><b>No hidden thresholds.</b> Every threshold comes from {@code QualityEligibilityPolicy};
 *       the resolvers carry no defaults and no production values.</li>
 *   <li><b>Fail closed.</b> {@code UNSAFE}, {@code NO_DATA}, stale, clock-skewed (and, by policy,
 *       future-event) snapshots are never eligible for trading; {@code null} input never becomes a
 *       zero, a NEUTRAL or an ELIGIBLE horizon; negative timing values are reported, not clamped.</li>
 *   <li><b>Per-feature degradation.</b> A failed calculator affects only the interpretation that
 *       depends on it: {@code trade-flow} failure fails every trade-flow horizon; a failed
 *       {@code bbo} / {@code order-book} / {@code short-term-regime} group is recorded and degrades the
 *       overall quality but does not fail a usable trade-flow horizon.</li>
 *   <li><b>No directional logic.</b> Nothing here decides BULLISH / BEARISH, computes a strength or
 *       creates an opportunity; that is the evaluators' stage. "At least one ELIGIBLE horizon" only
 *       means interpretation may continue.</li>
 * </ul>
 */
package com.trading.marketsignalengine.application.domain.interpretation.quality;
