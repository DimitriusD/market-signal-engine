package com.trading.marketsignalengine.application.domain.interpretation.book;

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

/** The book taxonomy is typed, well-formed, duplicate-free and deterministic. */
class BookReasonCodesTest {

    @Test
    void everyConstantIsListedOnceInPipelineOrder() throws IllegalAccessException {
        List<ReasonCode> declared = new ArrayList<>();
        for (Field field : BookReasonCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ReasonCode.class) {
                declared.add((ReasonCode) field.get(null));
            }
        }

        assertEquals(19, declared.size());
        assertEquals(new HashSet<>(declared), new HashSet<>(BookReasonCodes.ALL), "ALL lists every constant");
        assertEquals(BookReasonCodes.BOOK_NOT_SCOPED_TO_HORIZON, BookReasonCodes.ALL.get(0),
                "pipeline order: not-scoped first");
        assertEquals(BookReasonCodes.BOOK_NEUTRAL, BookReasonCodes.ALL.get(18),
                "pipeline order: direction last");
        assertEquals(new HashSet<>(BookReasonCodes.ALL).size(), BookReasonCodes.ALL.size(), "no duplicates");
        assertThrows(UnsupportedOperationException.class,
                () -> BookReasonCodes.ALL.add(BookReasonCodes.BOOK_NEUTRAL));
    }

    @Test
    void codesAreUpperSnakeCaseWithBookPrefix() {
        for (ReasonCode code : BookReasonCodes.ALL) {
            assertTrue(ReasonCode.FORMAT.matcher(code.value()).matches(), code.value());
            assertTrue(code.value().startsWith("BOOK_"), code.value());
        }
    }
}
