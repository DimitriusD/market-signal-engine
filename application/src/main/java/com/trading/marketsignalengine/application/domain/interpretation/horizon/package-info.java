/**
 * Per-horizon interpretation (Stage 6): turns the four independent evidence dimensions (FLOW,
 * MOMENTUM, VOLATILITY, BOOK) of one validated {@code MarketFeaturesSnapshot} into exactly one
 * {@code HorizonAssessment} per {@code MarketHorizon}. The single safe public entry point is
 * {@code HorizonAssessmentEvaluator.evaluate(snapshot, qualityAssessment, policy)} — it invokes every
 * evidence evaluator itself, so evidence computed from different snapshots can never be mixed; the
 * direction and regime reducers are deliberately package-private. Flow + Momentum are the primary
 * directional evidence, Book is confirmation/context only, Volatility never votes a direction (its
 * typed level drives the regime). Cross-horizon interpretation and market opportunity are the next
 * stage. No Spring, Kafka, Avro, I/O, metrics, clock or production defaults.
 */
package com.trading.marketsignalengine.application.domain.interpretation.horizon;
