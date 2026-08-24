package com.trading.marketsignalengine.application.domain.interpretation.opportunity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The opportunity reason taxonomy is typed, well-formed, duplicate-free and deterministic. */
class OpportunityReasonCodesTest {

    @Test
    void everyConstantIsListedOnceInResolutionOrder() throws IllegalAccessException {
        List<ReasonCode> declared = new ArrayList<>();
        for (Field field : OpportunityReasonCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ReasonCode.class) {
                declared.add((ReasonCode) field.get(null));
            }
        }

        assertEquals(23, declared.size());
        assertEquals(new HashSet<>(declared), new HashSet<>(OpportunityReasonCodes.ALL), "ALL lists every constant");
        assertEquals(OpportunityReasonCodes.OPPORTUNITY_BLOCKED_BY_QUALITY, OpportunityReasonCodes.ALL.get(0),
                "resolution order: the absolute quality gate first");
        assertEquals(OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY, OpportunityReasonCodes.ALL.get(1),
                "resolution order: negative verdict second");
        assertEquals(OpportunityReasonCodes.OPPORTUNITY_H15_MOMENTUM_NOT_CONFIRMED, OpportunityReasonCodes.ALL.get(7),
                "resolution order: evidence gates after the cross causes");
        assertEquals(OpportunityReasonCodes.OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE,
                OpportunityReasonCodes.ALL.get(15), "resolution order: candidate verdict after the gates");
        assertEquals(OpportunityReasonCodes.OPPORTUNITY_VOLATILE_REGIME_ALLOWED,
                OpportunityReasonCodes.ALL.get(22), "resolution order: candidate regime last");
        assertEquals(new HashSet<>(OpportunityReasonCodes.ALL).size(), OpportunityReasonCodes.ALL.size(),
                "no duplicates");
        assertThrows(UnsupportedOperationException.class,
                () -> OpportunityReasonCodes.ALL.add(OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY));
    }

    @Test
    void codesAreUpperSnakeCaseWithOpportunityPrefix() {
        for (ReasonCode code : OpportunityReasonCodes.ALL) {
            assertTrue(ReasonCode.FORMAT.matcher(code.value()).matches(), code.value());
            assertTrue(code.value().startsWith("OPPORTUNITY_"), code.value());
        }
    }
}
