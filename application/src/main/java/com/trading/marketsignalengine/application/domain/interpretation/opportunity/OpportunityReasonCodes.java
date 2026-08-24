package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.util.List;

/**
 * The typed reason taxonomy of opportunity resolution: every code a {@code MarketOpportunity}
 * produced by {@link OpportunityResolver} can carry. The codes describe the opportunity
 * <em>resolution</em> only — never the per-horizon, cross-horizon or nested evidence reasoning,
 * which already lives in the {@code CrossHorizonEvaluation}. Deterministic order within one result:
 * <ul>
 *   <li><b>BLOCKED</b> — {@link #OPPORTUNITY_BLOCKED_BY_QUALITY} first, then the typed quality
 *       reason codes in their original order;</li>
 *   <li><b>NO_OPPORTUNITY</b> — {@link #OPPORTUNITY_NO_OPPORTUNITY} first, then the cross-alignment
 *       cause, then the H15S/H5S evidence-gate causes, then the Book cause, then the strength cause,
 *       then the regime cause — every failed gate is reported, not only the first;</li>
 *   <li><b>CANDIDATE</b> — {@link #OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE}, the side, the
 *       H60S/H15S/H5S confirmations, then the regime reason.</li>
 * </ul>
 * Deliberately minimal; no free-form strings in the resolver.
 */
public final class OpportunityReasonCodes {

    private OpportunityReasonCodes() {
    }

    // ------------------------------------------------------------------ quality gate

    /** The snapshot is not {@code eligibleForTrading} — absolute priority over any market interpretation. */
    public static final ReasonCode OPPORTUNITY_BLOCKED_BY_QUALITY = ReasonCode.of("OPPORTUNITY_BLOCKED_BY_QUALITY");

    // ------------------------------------------------------------------ negative verdict and cross causes

    /** Quality allowed the evaluation, but no valid momentum-continuation setup was found. */
    public static final ReasonCode OPPORTUNITY_NO_OPPORTUNITY = ReasonCode.of("OPPORTUNITY_NO_OPPORTUNITY");
    /** Cross-horizon verdict PARTIALLY_ALIGNED — deliberately never a candidate in this version. */
    public static final ReasonCode OPPORTUNITY_CROSS_HORIZON_PARTIAL = ReasonCode.of("OPPORTUNITY_CROSS_HORIZON_PARTIAL");
    /** Cross-horizon verdict CONFLICTING. */
    public static final ReasonCode OPPORTUNITY_CROSS_HORIZON_CONFLICT =
            ReasonCode.of("OPPORTUNITY_CROSS_HORIZON_CONFLICT");
    /** Cross-horizon verdict NEUTRAL. */
    public static final ReasonCode OPPORTUNITY_CROSS_HORIZON_NEUTRAL = ReasonCode.of("OPPORTUNITY_CROSS_HORIZON_NEUTRAL");
    /** Cross-horizon verdict INSUFFICIENT_DATA. */
    public static final ReasonCode OPPORTUNITY_CROSS_HORIZON_INSUFFICIENT =
            ReasonCode.of("OPPORTUNITY_CROSS_HORIZON_INSUFFICIENT");
    /** Cross-horizon verdict UNKNOWN — never produced by the interpreter for valid typed input. */
    public static final ReasonCode OPPORTUNITY_CROSS_HORIZON_UNKNOWN = ReasonCode.of("OPPORTUNITY_CROSS_HORIZON_UNKNOWN");

    // ------------------------------------------------------------------ independent evidence gates

    /** The H15S MOMENTUM evidence is not AVAILABLE on the candidate direction — no persistence. */
    public static final ReasonCode OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED =
            ReasonCode.of("OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED");
    /** The H5S FLOW evidence is not AVAILABLE on the candidate direction — no active trigger. */
    public static final ReasonCode OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED =
            ReasonCode.of("OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED");
    /** AVAILABLE Book evidence on an eligible participating horizon is opposite to the candidate or MIXED. */
    public static final ReasonCode OPPORTUNITY_BOOK_CONTRADICTS = ReasonCode.of("OPPORTUNITY_BOOK_CONTRADICTS");

    // ------------------------------------------------------------------ strength gate

    /** The cross-horizon evidence strength is absent — a candidate needs a real aggregate strength. */
    public static final ReasonCode OPPORTUNITY_STRENGTH_UNAVAILABLE = ReasonCode.of("OPPORTUNITY_STRENGTH_UNAVAILABLE");
    /** The cross-horizon evidence strength is exactly zero — a real computed "no strength" reading. */
    public static final ReasonCode OPPORTUNITY_STRENGTH_ZERO = ReasonCode.of("OPPORTUNITY_STRENGTH_ZERO");

    // ------------------------------------------------------------------ regime gate

    /** The cross-horizon regime is UNKNOWN or absent — the risk/regime context is not defined enough. */
    public static final ReasonCode OPPORTUNITY_REGIME_UNKNOWN = ReasonCode.of("OPPORTUNITY_REGIME_UNKNOWN");
    /** RANGING / QUIET — not a sufficient context for the first momentum-continuation baseline. */
    public static final ReasonCode OPPORTUNITY_REGIME_NOT_CONTINUATION_COMPATIBLE =
            ReasonCode.of("OPPORTUNITY_REGIME_NOT_CONTINUATION_COMPATIBLE");
    /** VOLATILE regime with {@code allowVolatileMomentumContinuation = false}. */
    public static final ReasonCode OPPORTUNITY_VOLATILE_REGIME_BLOCKED_BY_POLICY =
            ReasonCode.of("OPPORTUNITY_VOLATILE_REGIME_BLOCKED_BY_POLICY");

    // ------------------------------------------------------------------ candidate verdict

    /** A momentum-continuation setup was identified — a candidate for strategy evaluation, not a command. */
    public static final ReasonCode OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE =
            ReasonCode.of("OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE");
    /** The candidate side is LONG — a long market setup is present, <b>not</b> "buy now". */
    public static final ReasonCode OPPORTUNITY_LONG = ReasonCode.of("OPPORTUNITY_LONG");
    /** The candidate side is SHORT — a short market setup is present, <b>not</b> "sell now". */
    public static final ReasonCode OPPORTUNITY_SHORT = ReasonCode.of("OPPORTUNITY_SHORT");

    // ------------------------------------------------------------------ candidate confirmations

    /** H60S senior context confirms the direction (from the full structural alignment). */
    public static final ReasonCode OPPORTUNITY_H60_CONTEXT_CONFIRMS = ReasonCode.of("OPPORTUNITY_H60_CONTEXT_CONFIRMS");
    /** H15S MOMENTUM evidence is AVAILABLE on the candidate direction — movement persistence. */
    public static final ReasonCode OPPORTUNITY_H15_MOMENTUM_CONFIRMS = ReasonCode.of("OPPORTUNITY_H15_MOMENTUM_CONFIRMS");
    /** H5S FLOW evidence is AVAILABLE on the candidate direction — the active trigger. */
    public static final ReasonCode OPPORTUNITY_H5_FLOW_TRIGGER_CONFIRMS =
            ReasonCode.of("OPPORTUNITY_H5_FLOW_TRIGGER_CONFIRMS");

    // ------------------------------------------------------------------ candidate regime

    /** The cross-horizon regime is TRENDING — continuation-compatible by default. */
    public static final ReasonCode OPPORTUNITY_TRENDING_REGIME = ReasonCode.of("OPPORTUNITY_TRENDING_REGIME");
    /** VOLATILE regime explicitly allowed by {@code allowVolatileMomentumContinuation = true}. */
    public static final ReasonCode OPPORTUNITY_VOLATILE_REGIME_ALLOWED =
            ReasonCode.of("OPPORTUNITY_VOLATILE_REGIME_ALLOWED");

    /** Every opportunity code, in the deterministic resolution order; unmodifiable, duplicate-free. */
    public static final List<ReasonCode> ALL = List.of(
            OPPORTUNITY_BLOCKED_BY_QUALITY,
            OPPORTUNITY_NO_OPPORTUNITY,
            OPPORTUNITY_CROSS_HORIZON_PARTIAL,
            OPPORTUNITY_CROSS_HORIZON_CONFLICT,
            OPPORTUNITY_CROSS_HORIZON_NEUTRAL,
            OPPORTUNITY_CROSS_HORIZON_INSUFFICIENT,
            OPPORTUNITY_CROSS_HORIZON_UNKNOWN,
            OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED,
            OPPORTUNITY_H5_FLOW_TRIGGER_NOT_CONFIRMED,
            OPPORTUNITY_BOOK_CONTRADICTS,
            OPPORTUNITY_STRENGTH_UNAVAILABLE,
            OPPORTUNITY_STRENGTH_ZERO,
            OPPORTUNITY_REGIME_UNKNOWN,
            OPPORTUNITY_REGIME_NOT_CONTINUATION_COMPATIBLE,
            OPPORTUNITY_VOLATILE_REGIME_BLOCKED_BY_POLICY,
            OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE,
            OPPORTUNITY_LONG,
            OPPORTUNITY_SHORT,
            OPPORTUNITY_H60_CONTEXT_CONFIRMS,
            OPPORTUNITY_H15_MOMENTUM_CONFIRMS,
            OPPORTUNITY_H5_FLOW_TRIGGER_CONFIRMS,
            OPPORTUNITY_TRENDING_REGIME,
            OPPORTUNITY_VOLATILE_REGIME_ALLOWED);
}
