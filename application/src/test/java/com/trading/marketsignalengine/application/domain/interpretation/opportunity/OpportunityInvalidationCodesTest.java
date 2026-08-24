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

/** The candidate invalidation taxonomy is typed, well-formed, duplicate-free and deterministic. */
class OpportunityInvalidationCodesTest {

    @Test
    void everyConstantIsListedOnceInGateOrder() throws IllegalAccessException {
        List<ReasonCode> declared = new ArrayList<>();
        for (Field field : OpportunityInvalidationCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ReasonCode.class) {
                declared.add((ReasonCode) field.get(null));
            }
        }

        assertEquals(7, declared.size());
        assertEquals(new HashSet<>(declared), new HashSet<>(OpportunityInvalidationCodes.ALL),
                "ALL lists every constant");
        assertEquals(List.of(
                OpportunityInvalidationCodes.OPPORTUNITY_INVALIDATE_QUALITY,
                OpportunityInvalidationCodes.OPPORTUNITY_INVALIDATE_CROSS_HORIZON_ALIGNMENT,
                OpportunityInvalidationCodes.OPPORTUNITY_INVALIDATE_H15_MOMENTUM,
                OpportunityInvalidationCodes.OPPORTUNITY_INVALIDATE_H5_FLOW_TRIGGER,
                OpportunityInvalidationCodes.OPPORTUNITY_INVALIDATE_BOOK_CONTRADICTION,
                OpportunityInvalidationCodes.OPPORTUNITY_INVALIDATE_STRENGTH,
                OpportunityInvalidationCodes.OPPORTUNITY_INVALIDATE_REGIME), OpportunityInvalidationCodes.ALL,
                "deterministic gate order");
        assertThrows(UnsupportedOperationException.class,
                () -> OpportunityInvalidationCodes.ALL.add(OpportunityInvalidationCodes.OPPORTUNITY_INVALIDATE_QUALITY));
    }

    @Test
    void codesAreUpperSnakeCaseWithInvalidatePrefix() {
        for (ReasonCode code : OpportunityInvalidationCodes.ALL) {
            assertTrue(ReasonCode.FORMAT.matcher(code.value()).matches(), code.value());
            assertTrue(code.value().startsWith("OPPORTUNITY_INVALIDATE_"), code.value());
        }
    }
}
