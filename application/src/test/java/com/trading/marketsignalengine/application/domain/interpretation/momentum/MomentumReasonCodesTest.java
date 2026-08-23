package com.trading.marketsignalengine.application.domain.interpretation.momentum;

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

/** The momentum taxonomy is typed, well-formed, duplicate-free and deterministic. */
class MomentumReasonCodesTest {

    @Test
    void everyConstantIsListedOnceInPipelineOrder() throws IllegalAccessException {
        List<ReasonCode> declared = new ArrayList<>();
        for (Field field : MomentumReasonCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ReasonCode.class) {
                declared.add((ReasonCode) field.get(null));
            }
        }

        assertEquals(8, declared.size());
        assertEquals(new HashSet<>(declared), new HashSet<>(MomentumReasonCodes.ALL), "ALL lists every constant");
        assertEquals(MomentumReasonCodes.MOMENTUM_NOT_SCOPED_TO_HORIZON, MomentumReasonCodes.ALL.get(0),
                "pipeline order: not-scoped first");
        assertEquals(MomentumReasonCodes.MOMENTUM_NEUTRAL_MOVE, MomentumReasonCodes.ALL.get(7),
                "pipeline order: direction last");
        assertEquals(new HashSet<>(MomentumReasonCodes.ALL).size(), MomentumReasonCodes.ALL.size(), "no duplicates");
        assertThrows(UnsupportedOperationException.class,
                () -> MomentumReasonCodes.ALL.add(MomentumReasonCodes.MOMENTUM_NEUTRAL_MOVE));
    }

    @Test
    void codesAreUpperSnakeCaseWithMomentumPrefix() {
        for (ReasonCode code : MomentumReasonCodes.ALL) {
            assertTrue(ReasonCode.FORMAT.matcher(code.value()).matches(), code.value());
            assertTrue(code.value().startsWith("MOMENTUM_"), code.value());
        }
    }
}
