package com.trading.marketsignalengine.application.domain.interpretation.cross;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * The typed reason taxonomy of cross-horizon interpretation: every code a
 * {@code CrossHorizonAssessment} produced by {@link CrossHorizonInterpreter} can carry. The codes
 * describe the cross-horizon <em>resolution</em> only — never the per-horizon or nested evidence
 * reasoning, which stays in the horizon assessments. Order within one assessment is deterministic:
 * final verdict first, then the dominant/anchor reason, then structural confirmation or conflict,
 * then the H5S trigger context, then the H1S micro-context, then the regime source. Deliberately
 * minimal; no free-form strings in the interpreter.
 */
public final class CrossHorizonReasonCodes {

    private CrossHorizonReasonCodes() {
    }

    // ------------------------------------------------------------------ final verdict

    /** H60S, H15S and H5S are all eligible, directional and bullish; H1S carries no adverse context. */
    public static final ReasonCode CROSS_HORIZON_ALIGNED_BULLISH = ReasonCode.of("CROSS_HORIZON_ALIGNED_BULLISH");
    /** H60S, H15S and H5S are all eligible, directional and bearish; H1S carries no adverse context. */
    public static final ReasonCode CROSS_HORIZON_ALIGNED_BEARISH = ReasonCode.of("CROSS_HORIZON_ALIGNED_BEARISH");
    /** A directional anchor with at least two structural confirmations, but not a full alignment. */
    public static final ReasonCode CROSS_HORIZON_PARTIALLY_ALIGNED = ReasonCode.of("CROSS_HORIZON_PARTIALLY_ALIGNED");
    /** After anchor selection at least one other structural horizon is opposite or MIXED. */
    public static final ReasonCode CROSS_HORIZON_CONFLICTING = ReasonCode.of("CROSS_HORIZON_CONFLICTING");
    /** No directional anchor, no structural conflict, all participating structural horizons NEUTRAL. */
    public static final ReasonCode CROSS_HORIZON_NEUTRAL = ReasonCode.of("CROSS_HORIZON_NEUTRAL");
    /** No usable cross-horizon conclusion; UNKNOWN is never a normal verdict for valid typed input. */
    public static final ReasonCode CROSS_HORIZON_INSUFFICIENT_DATA = ReasonCode.of("CROSS_HORIZON_INSUFFICIENT_DATA");

    // ------------------------------------------------------------------ anchor / structural confirmation

    /** The eligible directional H60S is the anchor and the dominant horizon (senior context). */
    public static final ReasonCode CROSS_H60_CONTEXT_DOMINANT = ReasonCode.of("CROSS_H60_CONTEXT_DOMINANT");
    /** No directional H60S; the eligible directional H15S is the anchor and dominant (market structure). */
    public static final ReasonCode CROSS_H15_STRUCTURE_DOMINANT = ReasonCode.of("CROSS_H15_STRUCTURE_DOMINANT");
    /** Neither H60S nor H15S is eligible and directional — no cross-horizon direction can exist. */
    public static final ReasonCode CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR =
            ReasonCode.of("CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR");
    /** An anchor exists but fewer than two structural horizons support its direction. */
    public static final ReasonCode CROSS_HORIZON_INSUFFICIENT_STRUCTURAL_CONFIRMATION =
            ReasonCode.of("CROSS_HORIZON_INSUFFICIENT_STRUCTURAL_CONFIRMATION");

    // ------------------------------------------------------------------ H5S trigger context

    /** The H5S trigger is directional and matches the anchor direction. */
    public static final ReasonCode CROSS_H5_TRIGGER_CONFIRMS = ReasonCode.of("CROSS_H5_TRIGGER_CONFIRMS");
    /** The H5S trigger is opposite to the anchor direction or MIXED (a structural conflict). */
    public static final ReasonCode CROSS_H5_TRIGGER_CONTRADICTS = ReasonCode.of("CROSS_H5_TRIGGER_CONTRADICTS");

    // ------------------------------------------------------------------ H1S micro-context

    /** H1S matches the cross-horizon direction; supportive context only — it adds no strength. */
    public static final ReasonCode CROSS_H1_SUPPORTS_CONTEXT = ReasonCode.of("CROSS_H1_SUPPORTS_CONTEXT");
    /** H1S is opposite to the cross-horizon direction or MIXED: strength is dropped, direction kept. */
    public static final ReasonCode CROSS_H1_ADVERSE_CONTEXT = ReasonCode.of("CROSS_H1_ADVERSE_CONTEXT");

    // ------------------------------------------------------------------ regime source

    /** The cross-horizon regime is the dominant horizon's usable regime. */
    public static final ReasonCode CROSS_HORIZON_REGIME_FROM_DOMINANT =
            ReasonCode.of("CROSS_HORIZON_REGIME_FROM_DOMINANT");
    /** The regime came from the first eligible participating horizon in role order H60S→H15S→H5S→H1S. */
    public static final ReasonCode CROSS_HORIZON_REGIME_FALLBACK = ReasonCode.of("CROSS_HORIZON_REGIME_FALLBACK");
    /** A cross assessment was formed but no participating horizon carries a usable regime. */
    public static final ReasonCode CROSS_HORIZON_REGIME_UNKNOWN = ReasonCode.of("CROSS_HORIZON_REGIME_UNKNOWN");

    /** Every cross-horizon code, in the deterministic resolution order; unmodifiable, duplicate-free. */
    public static final List<ReasonCode> ALL = List.of(
            CROSS_HORIZON_ALIGNED_BULLISH,
            CROSS_HORIZON_ALIGNED_BEARISH,
            CROSS_HORIZON_PARTIALLY_ALIGNED,
            CROSS_HORIZON_CONFLICTING,
            CROSS_HORIZON_NEUTRAL,
            CROSS_HORIZON_INSUFFICIENT_DATA,
            CROSS_H60_CONTEXT_DOMINANT,
            CROSS_H15_STRUCTURE_DOMINANT,
            CROSS_HORIZON_NO_DIRECTIONAL_ANCHOR,
            CROSS_HORIZON_INSUFFICIENT_STRUCTURAL_CONFIRMATION,
            CROSS_H5_TRIGGER_CONFIRMS,
            CROSS_H5_TRIGGER_CONTRADICTS,
            CROSS_H1_SUPPORTS_CONTEXT,
            CROSS_H1_ADVERSE_CONTEXT,
            CROSS_HORIZON_REGIME_FROM_DOMINANT,
            CROSS_HORIZON_REGIME_FALLBACK,
            CROSS_HORIZON_REGIME_UNKNOWN);
}
