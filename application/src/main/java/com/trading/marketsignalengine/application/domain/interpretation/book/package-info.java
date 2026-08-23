/**
 * Book V1 evidence: pure, deterministic classification of the <em>instantaneous</em> order-book state
 * ({@code BookFeature.top5Imbalance} + {@code BboFeature.micropriceOffsetBps}) into a {@code BOOK}
 * {@code EvidenceAssessment}. MFS v2 publishes one instantaneous book snapshot, not per-window book
 * features, so only the 1S horizon carries real book evidence; 5S/15S/60S are explicitly UNAVAILABLE
 * ({@code BOOK_NOT_SCOPED_TO_HORIZON}) — the 1S reading is never copied onto longer horizons. Spread
 * is validated as geometry but is never a directional vote (a later execution/liquidity gate). No
 * Spring, Kafka, Avro, I/O, metrics, clock or production defaults.
 */
package com.trading.marketsignalengine.application.domain.interpretation.book;
