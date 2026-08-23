package com.trading.marketsignalengine.application.domain.interpretation.horizon;

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

/** The horizon taxonomy is typed, well-formed, duplicate-free and deterministic. */
class HorizonReasonCodesTest {

    @Test
    void everyConstantIsListedOnceInResolutionOrder() throws IllegalAccessException {
        List<ReasonCode> declared = new ArrayList<>();
        for (Field field : HorizonReasonCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ReasonCode.class) {
                declared.add((ReasonCode) field.get(null));
            }
        }

        assertEquals(14, declared.size());
        assertEquals(new HashSet<>(declared), new HashSet<>(HorizonReasonCodes.ALL), "ALL lists every constant");
        assertEquals(HorizonReasonCodes.HORIZON_FLOW_MOMENTUM_CONFIRMED, HorizonReasonCodes.ALL.get(0),
                "resolution order: direction first");
        assertEquals(HorizonReasonCodes.HORIZON_BOOK_SUPPORTS_DIRECTION, HorizonReasonCodes.ALL.get(6),
                "resolution order: book context second");
        assertEquals(HorizonReasonCodes.HORIZON_REGIME_UNKNOWN, HorizonReasonCodes.ALL.get(13),
                "resolution order: regime last");
        assertEquals(new HashSet<>(HorizonReasonCodes.ALL).size(), HorizonReasonCodes.ALL.size(), "no duplicates");
        assertThrows(UnsupportedOperationException.class,
                () -> HorizonReasonCodes.ALL.add(HorizonReasonCodes.HORIZON_REGIME_UNKNOWN));
    }

    @Test
    void codesAreUpperSnakeCaseWithHorizonPrefix() {
        for (ReasonCode code : HorizonReasonCodes.ALL) {
            assertTrue(ReasonCode.FORMAT.matcher(code.value()).matches(), code.value());
            assertTrue(code.value().startsWith("HORIZON_"), code.value());
        }
    }
}
