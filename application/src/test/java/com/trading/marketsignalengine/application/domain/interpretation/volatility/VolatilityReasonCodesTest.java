package com.trading.marketsignalengine.application.domain.interpretation.volatility;

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

/** The volatility taxonomy is typed, well-formed, duplicate-free and deterministic. */
class VolatilityReasonCodesTest {

    @Test
    void everyConstantIsListedOnceInPipelineOrder() throws IllegalAccessException {
        List<ReasonCode> declared = new ArrayList<>();
        for (Field field : VolatilityReasonCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ReasonCode.class) {
                declared.add((ReasonCode) field.get(null));
            }
        }

        assertEquals(8, declared.size());
        assertEquals(new HashSet<>(declared), new HashSet<>(VolatilityReasonCodes.ALL), "ALL lists every constant");
        assertEquals(VolatilityReasonCodes.VOLATILITY_REGIME_CALCULATOR_FAILED, VolatilityReasonCodes.ALL.get(0),
                "pipeline order: failed first");
        assertEquals(VolatilityReasonCodes.VOLATILITY_EXTREME, VolatilityReasonCodes.ALL.get(7),
                "pipeline order: levels last");
        assertEquals(new HashSet<>(VolatilityReasonCodes.ALL).size(), VolatilityReasonCodes.ALL.size(), "no duplicates");
        assertThrows(UnsupportedOperationException.class,
                () -> VolatilityReasonCodes.ALL.add(VolatilityReasonCodes.VOLATILITY_LOW));
    }

    @Test
    void codesAreUpperSnakeCaseWithVolatilityPrefix() {
        for (ReasonCode code : VolatilityReasonCodes.ALL) {
            assertTrue(ReasonCode.FORMAT.matcher(code.value()).matches(), code.value());
            assertTrue(code.value().startsWith("VOLATILITY_"), code.value());
        }
    }
}
