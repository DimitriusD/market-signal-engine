package com.trading.marketsignalengine.application.domain.interpretation.book;

import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Book policy invariants: versioned lineage, positive depth minimum, top-5 thresholds inside the
 * imbalance range with a dead zone, microprice threshold geometry — and no spread threshold at all.
 */
class BookAssessmentPolicyTest {

    private static BookAssessmentPolicy policy(String version) {
        return new BookAssessmentPolicy(version, 5, bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50"));
    }

    @Test
    void validPolicyIsExposedWithValueEquality() {
        BookAssessmentPolicy policy = policy("book-v1");

        assertEquals("book-v1", policy.policyVersion());
        assertEquals(5, policy.minimumLevelsUsed());
        assertEquals(bd("0.30"), policy.bullishTop5ImbalanceThreshold());
        assertEquals(policy, policy("book-v1"));
        assertEquals(policy.hashCode(), policy("book-v1").hashCode());
        assertNotEquals(policy, policy("book-v2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "unknown", "TODO", "n/a", "placeholder"})
    void blankOrPlaceholderVersionIsRejected(String version) {
        assertThrows(IllegalArgumentException.class, () -> policy(version));
    }

    @Test
    void invalidParametersAreRejected() {
        // minimumLevelsUsed must be positive
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 0,
                bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50")));
        // top5 thresholds: bullish in (0, 1], bearish in [-1, 0)
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("1.1"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0.30"), bd("0"), bd("2"), bd("-2"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0.30"), bd("-1.1"), bd("2"), bd("-2"), bd("10"), bd("50")));
        // microprice thresholds must straddle zero
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0.30"), bd("-0.30"), bd("0"), bd("-2"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0.30"), bd("-0.30"), bd("2"), bd("1"), bd("10"), bd("50")));
        // full strength positive, below maxSafe, and covering both thresholds
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("0"), bd("50")));
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("10")));
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0.30"), bd("-0.30"), bd("15"), bd("-2"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0.30"), bd("-0.30"), bd("2"), bd("-15"), bd("10"), bd("50")));
        // nulls
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                null, bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50")));
        assertThrows(IllegalArgumentException.class, () -> new BookAssessmentPolicy("v1", 5,
                bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), null));
        // boundary geometry that is still valid: top5 thresholds at ±1, full == both micro thresholds
        BookAssessmentPolicy boundary = new BookAssessmentPolicy("v1", 1,
                BigDecimal.ONE, bd("-1"), bd("10"), bd("-10"), bd("10"), bd("10.01"));
        assertEquals(1, boundary.minimumLevelsUsed());
    }

    @Test
    void policyHasNoSpreadThreshold() {
        // spread is execution/liquidity, not directional book evidence — the policy must not model it
        for (var component : BookAssessmentPolicy.class.getRecordComponents()) {
            org.junit.jupiter.api.Assertions.assertFalse(
                    component.getName().toLowerCase(java.util.Locale.ROOT).contains("spread"),
                    "unexpected spread parameter: " + component.getName());
        }
    }
}
