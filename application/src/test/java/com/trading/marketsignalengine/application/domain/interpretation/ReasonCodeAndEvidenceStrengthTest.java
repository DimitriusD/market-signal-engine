package com.trading.marketsignalengine.application.domain.interpretation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReasonCodeAndEvidenceStrengthTest {

    // ------------------------------------------------------------------ ReasonCode

    @Test
    void reasonCodeIsTypedUpperSnakeCaseWithValueEquality() {
        ReasonCode a = ReasonCode.of("FLOW_IMBALANCE_ABOVE_THRESHOLD");
        ReasonCode b = new ReasonCode("FLOW_IMBALANCE_ABOVE_THRESHOLD");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals("FLOW_IMBALANCE_ABOVE_THRESHOLD", a.value());
        assertEquals("FLOW_IMBALANCE_ABOVE_THRESHOLD", a.toString());
        assertNotEquals(a, ReasonCode.of("STALE_TRADES"));
        assertTrue(ReasonCode.of("A").compareTo(ReasonCode.of("B")) < 0);
        // digits and single underscores are fine
        ReasonCode.of("WINDOW_5S_NOT_COMPUTED");
        ReasonCode.of("OK");
    }

    @Test
    void reasonCodeRejectsNullBlankAndNonSnakeCase() {
        assertThrows(NullPointerException.class, () -> ReasonCode.of(null));
        assertThrows(IllegalArgumentException.class, () -> ReasonCode.of(""));
        assertThrows(IllegalArgumentException.class, () -> ReasonCode.of("   "));
        assertThrows(IllegalArgumentException.class, () -> ReasonCode.of("stale_trades"));
        assertThrows(IllegalArgumentException.class, () -> ReasonCode.of("Stale-Trades"));
        assertThrows(IllegalArgumentException.class, () -> ReasonCode.of("STALE TRADES"));
        assertThrows(IllegalArgumentException.class, () -> ReasonCode.of("_STALE"));
        assertThrows(IllegalArgumentException.class, () -> ReasonCode.of("STALE_"));
        assertThrows(IllegalArgumentException.class, () -> ReasonCode.of("STALE__TRADES"));
        assertThrows(IllegalArgumentException.class, () -> ReasonCode.of("1ST"));
    }

    @Test
    void reasonCodeCollectionsRejectNullsAndDuplicatesAndAreImmutable() {
        List<ReasonCode> codes = Invariants.reasonCodes(
                List.of(ReasonCode.of("B_CODE"), ReasonCode.of("A_CODE")), "codes");
        assertEquals(List.of(ReasonCode.of("B_CODE"), ReasonCode.of("A_CODE")), codes, "insertion order preserved");
        assertThrows(UnsupportedOperationException.class, () -> codes.add(ReasonCode.of("C_CODE")));
        assertEquals(List.of(), Invariants.reasonCodes(null, "codes"));

        assertThrows(IllegalArgumentException.class, () -> Invariants.reasonCodes(
                List.of(ReasonCode.of("A_CODE"), ReasonCode.of("A_CODE")), "codes"));
        assertThrows(IllegalArgumentException.class, () -> Invariants.reasonCodes(
                java.util.Arrays.asList(ReasonCode.of("A_CODE"), null), "codes"));
    }

    // ------------------------------------------------------------------ EvidenceStrength

    @Test
    void evidenceStrengthAcceptsTheClosedUnitInterval() {
        assertEquals(EvidenceStrength.MIN, EvidenceStrength.of(BigDecimal.ZERO));
        assertEquals(EvidenceStrength.MAX, EvidenceStrength.of(BigDecimal.ONE));
        assertEquals("0", EvidenceStrength.of("0").toPlainString());
        assertEquals("0.5", EvidenceStrength.of("0.5").toPlainString());
        assertEquals("1", EvidenceStrength.of("1").toPlainString());
        assertEquals("0.35", EvidenceStrength.of(new BigDecimal("0.35")).toPlainString());
    }

    @Test
    void evidenceStrengthRejectsOutOfRangeNullAndNonDecimal() {
        assertThrows(IllegalArgumentException.class, () -> EvidenceStrength.of(new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> EvidenceStrength.of(new BigDecimal("1.01")));
        assertThrows(IllegalArgumentException.class, () -> EvidenceStrength.of("-1"));
        assertThrows(IllegalArgumentException.class, () -> EvidenceStrength.of("2"));
        assertThrows(IllegalArgumentException.class, () -> EvidenceStrength.of("NaN"));
        assertThrows(IllegalArgumentException.class, () -> EvidenceStrength.of("abc"));
        assertThrows(NullPointerException.class, () -> EvidenceStrength.of((BigDecimal) null));
        assertThrows(NullPointerException.class, () -> EvidenceStrength.of((String) null));
    }

    @Test
    void evidenceStrengthIsNormalisedSoEqualValuesAreEqualAndOutputIsDeterministic() {
        assertEquals(EvidenceStrength.of("0.50"), EvidenceStrength.of("0.5"));
        assertEquals(EvidenceStrength.of("0.50").hashCode(), EvidenceStrength.of("0.5").hashCode());
        assertEquals(EvidenceStrength.of("1.000"), EvidenceStrength.MAX);
        assertEquals(EvidenceStrength.of("0.00"), EvidenceStrength.MIN);
        assertEquals("0", EvidenceStrength.of(new BigDecimal("0E-10")).toPlainString());
        assertEquals("1", EvidenceStrength.of("1.00").toPlainString());
        assertEquals("0.5", EvidenceStrength.of("0.500").toString());
        assertEquals(0, EvidenceStrength.of("0.25").compareTo(EvidenceStrength.of("0.250")));
        assertTrue(EvidenceStrength.of("0.2").compareTo(EvidenceStrength.of("0.3")) < 0);
    }
}
