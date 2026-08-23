package com.trading.marketsignalengine.application.domain.interpretation;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable, machine-readable reason code explaining a verdict of the V2 interpretation model (quality,
 * eligibility, evidence, horizon, cross-horizon, opportunity, invalidation). A typed wrapper instead
 * of raw strings: non-null, non-blank, {@code UPPER_SNAKE_CASE} ({@code [A-Z][A-Z0-9]*(_[A-Z0-9]+)*}),
 * value-based equality. The concrete taxonomy is not an enum on purpose — it grows with the evaluators
 * — but every code that exists is well-formed.
 */
public record ReasonCode(String value) implements Comparable<ReasonCode> {

    /** {@code UPPER_SNAKE_CASE}: upper-case letters and digits, single underscores, no leading/trailing underscore. */
    public static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");

    public ReasonCode {
        Objects.requireNonNull(value, "reason code value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("reason code must not be blank");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "reason code '" + value + "' must be UPPER_SNAKE_CASE (" + FORMAT.pattern() + ")");
        }
    }

    public static ReasonCode of(String value) {
        return new ReasonCode(value);
    }

    @Override
    public int compareTo(ReasonCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
