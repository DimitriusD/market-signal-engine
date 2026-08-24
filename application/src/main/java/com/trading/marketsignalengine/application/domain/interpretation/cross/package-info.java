/**
 * Hierarchical cross-horizon interpretation (Stage 7): reduces the four per-horizon
 * {@code HorizonAssessment}s of one snapshot into one {@code CrossHorizonAssessment}. The hierarchy is
 * fixed algorithm semantics, not configuration: H60S is the senior market context, H15S the market
 * structure, H5S the trade trigger — these three are the <em>structural</em> horizons — and H1S is
 * micro/execution context only (never an anchor, never a structural confirmation, never a conflicting
 * horizon; it can support a conclusion or flag adverse micro-context via reason codes). There is no
 * majority voting, no averaging, no numeric weights and no strongest-horizon selection. The single
 * safe public entry point is {@code CrossHorizonAssessmentEvaluator.evaluate(snapshot,
 * qualityAssessment, policy)} — it derives the horizon assessments itself, so manually assembled
 * assessments from different snapshots can never be interpreted together; the interpreter is
 * deliberately package-private. No opportunity, BUY/SELL or execution decision is produced here. No
 * Spring, Kafka, Avro, I/O, metrics, clock or production defaults.
 */
package com.trading.marketsignalengine.application.domain.interpretation.cross;
