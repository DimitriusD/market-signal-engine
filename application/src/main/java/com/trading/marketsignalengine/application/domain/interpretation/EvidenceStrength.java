package com.trading.marketsignalengine.application.domain.interpretation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Heuristic strength of a piece of evidence, of a horizon conclusion, of the cross-horizon conclusion
 * or of an opportunity, as a decimal in {@code [0, 1]}. It is <b>not</b> a probability and <b>not</b> a
 * calibrated confidence — it is uncalibrated heuristic evidence; calibration is a later, replay-driven
 * stage. Absence of strength is modelled as an absent ({@code null}) {@code EvidenceStrength} on the
 * owning object, never as {@code 0}: zero is a real, computed "no strength" reading.
 *
 * <p>Exact {@link BigDecimal} arithmetic only: no {@code double} factory (binary floating point, NaN
 * and ±∞ have no place in a deterministic, replayable output). The value is normalised to its shortest
 * plain representation ({@code 0.50 → 0.5}, {@code 1.00 → 1}) so that equal strengths are equal
 * values and {@link #toPlainString()} is deterministic regardless of how the producer scaled it.
 */
public record EvidenceStrength(BigDecimal value) implements Comparable<EvidenceStrength> {

    public static final EvidenceStrength MIN = new EvidenceStrength(BigDecimal.ZERO);
    public static final EvidenceStrength MAX = new EvidenceStrength(BigDecimal.ONE);

    public EvidenceStrength {
        Objects.requireNonNull(value, "evidence strength value (absence is a null EvidenceStrength, not a null value)");
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("evidence strength must be within [0, 1], got " + value.toPlainString());
        }
        value = normalise(value);
    }

    public static EvidenceStrength of(BigDecimal value) {
        return new EvidenceStrength(value);
    }

    /** Parses a plain decimal string ({@code "0.35"}); the same validation as {@link #of(BigDecimal)}. */
    public static EvidenceStrength of(String plainDecimal) {
        Objects.requireNonNull(plainDecimal, "plainDecimal");
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(plainDecimal.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("evidence strength '" + plainDecimal + "' is not a decimal", e);
        }
        return new EvidenceStrength(parsed);
    }

    /** Deterministic plain decimal representation ({@code 0}, {@code 0.5}, {@code 1}); never scientific notation. */
    public String toPlainString() {
        return value.toPlainString();
    }

    @Override
    public int compareTo(EvidenceStrength other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return toPlainString();
    }

    private static BigDecimal normalise(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        // stripTrailingZeros turns 0.00 into 0 and 1.00 into 1; a negative scale cannot occur in [0, 1]
        // except for exact 0 (0E-? forms), which setScale(0) normalises back to a plain 0.
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
