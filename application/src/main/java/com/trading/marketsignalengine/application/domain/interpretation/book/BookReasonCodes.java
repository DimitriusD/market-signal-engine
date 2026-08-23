package com.trading.marketsignalengine.application.domain.interpretation.book;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * The typed reason taxonomy of the Book V1 evaluator: every code a {@code BOOK}
 * {@code EvidenceAssessment} produced by {@link BookAssessmentEvaluator} can carry <em>in addition
 * to</em> the Stage 3 eligibility reasons, which are kept verbatim (never renamed or re-encoded).
 * Deliberately minimal; one constant per distinct verdict, no free-form strings in the evaluator.
 */
public final class BookReasonCodes {

    private BookReasonCodes() {
    }

    // ------------------------------------------------------------------ not scoped → UNAVAILABLE

    /** MFS v2 publishes one instantaneous book snapshot; 5S/15S/60S have no rolling book features. */
    public static final ReasonCode BOOK_NOT_SCOPED_TO_HORIZON = ReasonCode.of("BOOK_NOT_SCOPED_TO_HORIZON");

    // ------------------------------------------------------------------ failed input → FAILED

    /** {@code diagnostics.failedFeatureGroups} contains {@code bbo}. */
    public static final ReasonCode BOOK_BBO_CALCULATOR_FAILED = ReasonCode.of("BOOK_BBO_CALCULATOR_FAILED");
    /** {@code diagnostics.failedFeatureGroups} contains {@code order-book}. */
    public static final ReasonCode BOOK_ORDER_BOOK_CALCULATOR_FAILED = ReasonCode.of("BOOK_ORDER_BOOK_CALCULATOR_FAILED");

    // ------------------------------------------------------------------ book-specific quality → UNTRUSTED

    /** {@code quality.sourceOrderBookTrusted == false}. */
    public static final ReasonCode BOOK_SOURCE_UNTRUSTED = ReasonCode.of("BOOK_SOURCE_UNTRUSTED");
    /** {@code quality.syncStatus != IN_SYNC}. */
    public static final ReasonCode BOOK_NOT_IN_SYNC = ReasonCode.of("BOOK_NOT_IN_SYNC");
    /** {@code quality.staleOrderBookState == true}. */
    public static final ReasonCode BOOK_STALE = ReasonCode.of("BOOK_STALE");
    /** {@code quality.incompleteBook == true}. */
    public static final ReasonCode BOOK_INCOMPLETE = ReasonCode.of("BOOK_INCOMPLETE");

    // ------------------------------------------------------------------ invalid input → UNTRUSTED

    /** Structurally impossible BBO geometry (non-positive/crossed prices, negative spread or quantity, microprice outside [bid, ask]). */
    public static final ReasonCode BOOK_BBO_INVALID = ReasonCode.of("BOOK_BBO_INVALID");
    /** {@code BookFeature.levelsUsed <= 0}: a book feature computed from no levels is corrupt. */
    public static final ReasonCode BOOK_LEVELS_INVALID = ReasonCode.of("BOOK_LEVELS_INVALID");
    /** {@code top1Imbalance} lies outside {@code [-1, 1]} (range-checked even though it is not a directional vote yet). */
    public static final ReasonCode BOOK_TOP1_IMBALANCE_OUT_OF_RANGE = ReasonCode.of("BOOK_TOP1_IMBALANCE_OUT_OF_RANGE");
    /** {@code top5Imbalance} lies outside {@code [-1, 1]}. */
    public static final ReasonCode BOOK_TOP5_IMBALANCE_OUT_OF_RANGE = ReasonCode.of("BOOK_TOP5_IMBALANCE_OUT_OF_RANGE");
    /** {@code abs(micropriceOffsetBps) > policy.maxSafeAbsMicropriceOffsetBps}: implausible offset. */
    public static final ReasonCode BOOK_MICROPRICE_OFFSET_OUT_OF_SAFE_RANGE =
            ReasonCode.of("BOOK_MICROPRICE_OFFSET_OUT_OF_SAFE_RANGE");

    // ------------------------------------------------------------------ availability / composition

    /** {@code 0 < levelsUsed < policy.minimumLevelsUsed}: the top-5 indicator is not deep enough to use. */
    public static final ReasonCode BOOK_INSUFFICIENT_DEPTH = ReasonCode.of("BOOK_INSUFFICIENT_DEPTH");
    /** Neither usable indicator is present; a missing indicator is never a zero or neutral reading. */
    public static final ReasonCode BOOK_INDICATORS_MISSING = ReasonCode.of("BOOK_INDICATORS_MISSING");
    /** Only one of the two indicators backed the verdict. */
    public static final ReasonCode BOOK_PARTIAL_EVIDENCE = ReasonCode.of("BOOK_PARTIAL_EVIDENCE");
    /** The two indicators point in opposite directions → MIXED, no strength. */
    public static final ReasonCode BOOK_INDICATORS_CONFLICT = ReasonCode.of("BOOK_INDICATORS_CONFLICT");

    // ------------------------------------------------------------------ computed, AVAILABLE

    /** The combined book reading is bullish. */
    public static final ReasonCode BOOK_BULLISH = ReasonCode.of("BOOK_BULLISH");
    /** The combined book reading is bearish. */
    public static final ReasonCode BOOK_BEARISH = ReasonCode.of("BOOK_BEARISH");
    /** The combined book reading is neutral (a real, interpreted "no direction"). */
    public static final ReasonCode BOOK_NEUTRAL = ReasonCode.of("BOOK_NEUTRAL");

    /** Every book code, in the deterministic order of the evaluation pipeline; unmodifiable, duplicate-free. */
    public static final List<ReasonCode> ALL = List.of(
            BOOK_NOT_SCOPED_TO_HORIZON,
            BOOK_BBO_CALCULATOR_FAILED,
            BOOK_ORDER_BOOK_CALCULATOR_FAILED,
            BOOK_SOURCE_UNTRUSTED,
            BOOK_NOT_IN_SYNC,
            BOOK_STALE,
            BOOK_INCOMPLETE,
            BOOK_BBO_INVALID,
            BOOK_LEVELS_INVALID,
            BOOK_TOP1_IMBALANCE_OUT_OF_RANGE,
            BOOK_TOP5_IMBALANCE_OUT_OF_RANGE,
            BOOK_MICROPRICE_OFFSET_OUT_OF_SAFE_RANGE,
            BOOK_INSUFFICIENT_DEPTH,
            BOOK_INDICATORS_MISSING,
            BOOK_PARTIAL_EVIDENCE,
            BOOK_INDICATORS_CONFLICT,
            BOOK_BULLISH,
            BOOK_BEARISH,
            BOOK_NEUTRAL);
}
