package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * The typed reason taxonomy of horizon-level resolution: every code a {@code HorizonAssessment}
 * produced by {@link HorizonAssessmentEvaluator} can carry. These codes describe the horizon-level
 * <em>resolution</em> (directional alignment, divergence, book context, regime) — never the nested
 * evidence, which keeps its own explanations. A non-eligible horizon carries its eligibility reasons
 * verbatim instead, with no resolution codes. Order within one assessment: directional resolution
 * first, then book context, then regime. Deliberately minimal; no free-form strings in the resolvers.
 */
public final class HorizonReasonCodes {

    private HorizonReasonCodes() {
    }

    // ------------------------------------------------------------------ directional resolution

    /** Flow and Momentum are both directional and agree; strength = min of the two. */
    public static final ReasonCode HORIZON_FLOW_MOMENTUM_CONFIRMED = ReasonCode.of("HORIZON_FLOW_MOMENTUM_CONFIRMED");
    /** Flow and Momentum are both directional and disagree → MIXED, no strength; never a reversal signal. */
    public static final ReasonCode HORIZON_FLOW_MOMENTUM_DIVERGENCE = ReasonCode.of("HORIZON_FLOW_MOMENTUM_DIVERGENCE");
    /** Only Flow is directional (Momentum neutral / unknown / not available) — unconfirmed direction. */
    public static final ReasonCode HORIZON_DIRECTION_FROM_FLOW = ReasonCode.of("HORIZON_DIRECTION_FROM_FLOW");
    /** Only Momentum is directional (Flow not directional) — unconfirmed direction. */
    public static final ReasonCode HORIZON_DIRECTION_FROM_MOMENTUM = ReasonCode.of("HORIZON_DIRECTION_FROM_MOMENTUM");
    /** Every primary dimension read AVAILABLE + NEUTRAL — a real interpreted "no direction". */
    public static final ReasonCode HORIZON_DIRECTION_NEUTRAL = ReasonCode.of("HORIZON_DIRECTION_NEUTRAL");
    /** No directional evidence and the all-neutral condition not met → UNKNOWN, never NEUTRAL. */
    public static final ReasonCode HORIZON_DIRECTION_INSUFFICIENT = ReasonCode.of("HORIZON_DIRECTION_INSUFFICIENT");

    // ------------------------------------------------------------------ book context

    /** AVAILABLE Book agrees with the primary direction; direction and strength unchanged. */
    public static final ReasonCode HORIZON_BOOK_SUPPORTS_DIRECTION = ReasonCode.of("HORIZON_BOOK_SUPPORTS_DIRECTION");
    /** AVAILABLE Book opposes the primary direction (or is MIXED): direction kept, strength dropped to null. */
    public static final ReasonCode HORIZON_BOOK_CONTRADICTS_DIRECTION =
            ReasonCode.of("HORIZON_BOOK_CONTRADICTS_DIRECTION");
    /** AVAILABLE + NEUTRAL Book next to a directional conclusion; direction and strength unchanged. */
    public static final ReasonCode HORIZON_BOOK_NEUTRAL = ReasonCode.of("HORIZON_BOOK_NEUTRAL");

    // ------------------------------------------------------------------ regime resolution

    /** Low/normal volatility with directional Momentum. */
    public static final ReasonCode HORIZON_REGIME_TRENDING = ReasonCode.of("HORIZON_REGIME_TRENDING");
    /** Normal volatility with an AVAILABLE NEUTRAL Momentum. */
    public static final ReasonCode HORIZON_REGIME_RANGING = ReasonCode.of("HORIZON_REGIME_RANGING");
    /** Volatility level HIGH or EXTREME; Momentum does not change this verdict. */
    public static final ReasonCode HORIZON_REGIME_VOLATILE = ReasonCode.of("HORIZON_REGIME_VOLATILE");
    /** Low volatility without directional Momentum (including 1S, where momentum is not scoped). */
    public static final ReasonCode HORIZON_REGIME_QUIET = ReasonCode.of("HORIZON_REGIME_QUIET");
    /** Assessed but not classifiable (volatility not usable, or normal volatility with unknown momentum). */
    public static final ReasonCode HORIZON_REGIME_UNKNOWN = ReasonCode.of("HORIZON_REGIME_UNKNOWN");

    /** Every horizon code, in the deterministic resolution order; unmodifiable, duplicate-free. */
    public static final List<ReasonCode> ALL = List.of(
            HORIZON_FLOW_MOMENTUM_CONFIRMED,
            HORIZON_FLOW_MOMENTUM_DIVERGENCE,
            HORIZON_DIRECTION_FROM_FLOW,
            HORIZON_DIRECTION_FROM_MOMENTUM,
            HORIZON_DIRECTION_NEUTRAL,
            HORIZON_DIRECTION_INSUFFICIENT,
            HORIZON_BOOK_SUPPORTS_DIRECTION,
            HORIZON_BOOK_CONTRADICTS_DIRECTION,
            HORIZON_BOOK_NEUTRAL,
            HORIZON_REGIME_TRENDING,
            HORIZON_REGIME_RANGING,
            HORIZON_REGIME_VOLATILE,
            HORIZON_REGIME_QUIET,
            HORIZON_REGIME_UNKNOWN);
}
