package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNotPlaceholder;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Versioned, immutable validity parameters of one interpretation snapshot. All values are explicit
 * whole-millisecond durations — no production defaults, no confidence / edge / cost / probability /
 * execution-latency terms and no adaptive decay at this stage.
 *
 * <p>Base validities anchor to the source market tick: {@code validUntil = sourceEvaluationAt +
 * baseValidity − deductions}, an <b>exclusive</b> absolute deadline. The momentum-continuation base
 * validity is horizon-aware — the map carries exactly the four canonical horizons (the setup horizon
 * selects the duration) even though the current resolver only produces H5S candidates.
 * {@code noOpportunityBaseValidity} / {@code blockedBaseValidity} bound how long a negative / blocked
 * verdict stays representative. Deductions: {@code publicationSafetyBuffer} always applies;
 * {@code degradedQualityAdjustment} and {@code volatileRegimeAdjustment} apply to candidates only.
 *
 * <p>Enforced invariants: base durations strictly positive (≥ 1 ms), adjustments non-negative, all
 * whole-millisecond and representable as long milliseconds; the summed candidate deductions must not
 * overflow and every base validity must strictly exceed its maximum applicable deductions — so the
 * policy itself can never produce {@code validUntil <= evaluatedAt}. The horizon map is defensively
 * copied into an unmodifiable {@link EnumMap} (canonical iteration order, deterministic access).
 */
public record InterpretationValidityPolicy(
        String policyVersion,
        Map<MarketHorizon, Duration> momentumContinuationBaseValidity,
        Duration noOpportunityBaseValidity,
        Duration blockedBaseValidity,
        Duration publicationSafetyBuffer,
        Duration degradedQualityAdjustment,
        Duration volatileRegimeAdjustment) {

    public InterpretationValidityPolicy {
        requireNotPlaceholder(policyVersion, "validity policyVersion");
        momentumContinuationBaseValidity = canonicalBaseValidities(momentumContinuationBaseValidity);
        requireBaseValidity(noOpportunityBaseValidity, "noOpportunityBaseValidity");
        requireBaseValidity(blockedBaseValidity, "blockedBaseValidity");
        requireAdjustment(publicationSafetyBuffer, "publicationSafetyBuffer");
        requireAdjustment(degradedQualityAdjustment, "degradedQualityAdjustment");
        requireAdjustment(volatileRegimeAdjustment, "volatileRegimeAdjustment");

        long bufferMs = wholeMillis(publicationSafetyBuffer, "publicationSafetyBuffer");
        long maxCandidateDeductionsMs = addExactMs(
                addExactMs(bufferMs, wholeMillis(degradedQualityAdjustment, "degradedQualityAdjustment")),
                wholeMillis(volatileRegimeAdjustment, "volatileRegimeAdjustment"));
        for (Map.Entry<MarketHorizon, Duration> entry : momentumContinuationBaseValidity.entrySet()) {
            require(wholeMillis(entry.getValue(), "momentumContinuationBaseValidity") > maxCandidateDeductionsMs,
                    "momentumContinuationBaseValidity[" + entry.getKey() + "] = " + entry.getValue().toMillis()
                            + " ms must strictly exceed the maximum candidate deductions " + maxCandidateDeductionsMs + " ms");
        }
        require(wholeMillis(noOpportunityBaseValidity, "noOpportunityBaseValidity") > bufferMs,
                "noOpportunityBaseValidity must strictly exceed publicationSafetyBuffer " + bufferMs + " ms");
        require(wholeMillis(blockedBaseValidity, "blockedBaseValidity") > bufferMs,
                "blockedBaseValidity must strictly exceed publicationSafetyBuffer " + bufferMs + " ms");
    }

    /** Base validity of a momentum-continuation candidate on {@code horizon}; never {@code null}. */
    public Duration momentumContinuationBaseValidityOf(MarketHorizon horizon) {
        requireNonNull(horizon, "horizon");
        return momentumContinuationBaseValidity.get(horizon);
    }

    // ------------------------------------------------------------------ validation helpers

    private static Map<MarketHorizon, Duration> canonicalBaseValidities(Map<MarketHorizon, Duration> byHorizon) {
        requireNonNull(byHorizon, "momentumContinuationBaseValidity");
        EnumMap<MarketHorizon, Duration> copy = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            Duration duration = byHorizon.get(horizon);
            require(duration != null, "momentumContinuationBaseValidity is missing horizon " + horizon
                    + " (exactly one duration per " + MarketHorizon.canonicalOrder() + " is required)");
            requireBaseValidity(duration, "momentumContinuationBaseValidity[" + horizon + "]");
            copy.put(horizon, duration);
        }
        // fail fast instead of silently dropping anything beyond the four canonical keys (e.g. a null key)
        require(byHorizon.size() == copy.size(),
                "momentumContinuationBaseValidity must contain exactly the four canonical horizons, got keys "
                        + byHorizon.keySet());
        return Collections.unmodifiableMap(copy);
    }

    private static void requireBaseValidity(Duration duration, String field) {
        requireNonNull(duration, field);
        require(!duration.isNegative() && !duration.isZero(),
                field + " must be strictly positive (at least 1 ms), got " + duration);
        wholeMillis(duration, field);
    }

    private static void requireAdjustment(Duration duration, String field) {
        requireNonNull(duration, field);
        require(!duration.isNegative(), field + " must be non-negative, got " + duration);
        wholeMillis(duration, field);
    }

    /** The duration as long milliseconds; rejects sub-millisecond precision and long overflow. */
    private static long wholeMillis(Duration duration, String field) {
        require(duration.getNano() % 1_000_000 == 0,
                field + " must have whole-millisecond precision, got " + duration);
        try {
            return duration.toMillis();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(field + " is not representable as long milliseconds: " + duration, e);
        }
    }

    private static long addExactMs(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("validity deductions overflow long milliseconds: " + a + " + " + b, e);
        }
    }
}
