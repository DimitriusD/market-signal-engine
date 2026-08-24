package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import org.junit.jupiter.api.Test;

/** The validity reason taxonomy is typed, well-formed and immutable. */
class InterpretationValidityReasonCodesTest {

    @Test
    void catalogIsMinimalAndImmutable() {
        assertEquals(java.util.List.of(InterpretationValidityReasonCodes.OPPORTUNITY_EXPIRED_BEFORE_ASSEMBLY),
                InterpretationValidityReasonCodes.ALL);
        assertThrows(UnsupportedOperationException.class, () -> InterpretationValidityReasonCodes.ALL
                .add(InterpretationValidityReasonCodes.OPPORTUNITY_EXPIRED_BEFORE_ASSEMBLY));
    }

    @Test
    void codesAreUpperSnakeCaseWithOpportunityPrefix() {
        for (ReasonCode code : InterpretationValidityReasonCodes.ALL) {
            assertTrue(ReasonCode.FORMAT.matcher(code.value()).matches(), code.value());
            assertTrue(code.value().startsWith("OPPORTUNITY_"), code.value());
        }
    }
}
