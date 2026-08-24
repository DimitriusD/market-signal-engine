package com.trading.marketsignalengine.application.domain.interpretation.cross;

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

/** The cross-horizon taxonomy is typed, well-formed, duplicate-free and deterministic. */
class CrossHorizonReasonCodesTest {

    @Test
    void everyConstantIsListedOnceInResolutionOrder() throws IllegalAccessException {
        List<ReasonCode> declared = new ArrayList<>();
        for (Field field : CrossHorizonReasonCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ReasonCode.class) {
                declared.add((ReasonCode) field.get(null));
            }
        }

        assertEquals(17, declared.size());
        assertEquals(new HashSet<>(declared), new HashSet<>(CrossHorizonReasonCodes.ALL), "ALL lists every constant");
        assertEquals(CrossHorizonReasonCodes.CROSS_HORIZON_ALIGNED_BULLISH, CrossHorizonReasonCodes.ALL.get(0),
                "resolution order: final verdict first");
        assertEquals(CrossHorizonReasonCodes.CROSS_H60_CONTEXT_DOMINANT, CrossHorizonReasonCodes.ALL.get(6),
                "resolution order: anchor / structural confirmation second");
        assertEquals(CrossHorizonReasonCodes.CROSS_H5_TRIGGER_CONFIRMS, CrossHorizonReasonCodes.ALL.get(10),
                "resolution order: H5S trigger context third");
        assertEquals(CrossHorizonReasonCodes.CROSS_H1_SUPPORTS_CONTEXT, CrossHorizonReasonCodes.ALL.get(12),
                "resolution order: H1S micro-context fourth");
        assertEquals(CrossHorizonReasonCodes.CROSS_HORIZON_REGIME_UNKNOWN, CrossHorizonReasonCodes.ALL.get(16),
                "resolution order: regime source last");
        assertEquals(new HashSet<>(CrossHorizonReasonCodes.ALL).size(), CrossHorizonReasonCodes.ALL.size(),
                "no duplicates");
        assertThrows(UnsupportedOperationException.class,
                () -> CrossHorizonReasonCodes.ALL.add(CrossHorizonReasonCodes.CROSS_HORIZON_REGIME_UNKNOWN));
    }

    @Test
    void codesAreUpperSnakeCaseWithCrossPrefix() {
        for (ReasonCode code : CrossHorizonReasonCodes.ALL) {
            assertTrue(ReasonCode.FORMAT.matcher(code.value()).matches(), code.value());
            assertTrue(code.value().startsWith("CROSS_"), code.value());
        }
    }
}
