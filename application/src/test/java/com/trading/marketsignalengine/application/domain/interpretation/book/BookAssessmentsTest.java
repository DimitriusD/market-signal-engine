package com.trading.marketsignalengine.application.domain.interpretation.book;

import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exactly four BOOK assessments, canonical order, BOOK dimension only, fail-fast lookups, immutable. */
class BookAssessmentsTest {

    private static final EvidenceAssessment BULLISH = EvidenceAssessment.available(EvidenceDimension.BOOK,
            InterpretationDirection.BULLISH, EvidenceStrength.of("0.6"), List.of(BookReasonCodes.BOOK_BULLISH));
    private static final EvidenceAssessment NOT_SCOPED = EvidenceAssessment.unavailable(EvidenceDimension.BOOK,
            List.of(BookReasonCodes.BOOK_NOT_SCOPED_TO_HORIZON));
    private static final EvidenceAssessment MIXED = EvidenceAssessment.available(EvidenceDimension.BOOK,
            InterpretationDirection.MIXED, null, List.of(BookReasonCodes.BOOK_INDICATORS_CONFLICT));

    @Test
    void storesExactlyOnePerHorizonInCanonicalOrderRegardlessOfInputOrder() {
        Map<MarketHorizon, EvidenceAssessment> unordered = new HashMap<>();
        unordered.put(H60S, NOT_SCOPED);
        unordered.put(H1S, BULLISH);
        unordered.put(H15S, NOT_SCOPED);
        unordered.put(H5S, MIXED);

        BookAssessments assessments = new BookAssessments(unordered);

        assertEquals(List.of(H1S, H5S, H15S, H60S), new ArrayList<>(assessments.asMap().keySet()));
        assertEquals(List.of(BULLISH, MIXED, NOT_SCOPED, NOT_SCOPED), assessments.asList());
        assertSame(BULLISH, assessments.of(H1S));
        assertEquals(assessments, BookAssessments.of(BULLISH, MIXED, NOT_SCOPED, NOT_SCOPED));
        assertEquals(assessments.hashCode(), BookAssessments.of(BULLISH, MIXED, NOT_SCOPED, NOT_SCOPED).hashCode());
        assertNotEquals(assessments, BookAssessments.of(BULLISH, NOT_SCOPED, NOT_SCOPED, NOT_SCOPED));
    }

    @Test
    void missingHorizonFailsFast() {
        Map<MarketHorizon, EvidenceAssessment> partial = new EnumMap<>(MarketHorizon.class);
        partial.put(H1S, BULLISH);
        partial.put(H5S, NOT_SCOPED);
        partial.put(H15S, NOT_SCOPED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BookAssessments(partial));
        assertTrue(ex.getMessage().contains("H60S"), ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new BookAssessments(null));
        assertThrows(IllegalArgumentException.class, () -> BookAssessments.of(BULLISH, null, NOT_SCOPED, NOT_SCOPED));
    }

    @Test
    void entriesBeyondTheFourCanonicalKeysAreRejectedNotSilentlyDropped() {
        Map<MarketHorizon, EvidenceAssessment> withNullKey = new HashMap<>();
        withNullKey.put(H1S, BULLISH);
        withNullKey.put(H5S, NOT_SCOPED);
        withNullKey.put(H15S, NOT_SCOPED);
        withNullKey.put(H60S, NOT_SCOPED);
        withNullKey.put(null, NOT_SCOPED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BookAssessments(withNullKey));
        assertTrue(ex.getMessage().contains("exactly the four canonical horizons"), ex.getMessage());
    }

    @Test
    void rejectsNonBookDimension() {
        EvidenceAssessment volatility = EvidenceAssessment.unavailable(EvidenceDimension.VOLATILITY, List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BookAssessments.of(BULLISH, volatility, NOT_SCOPED, NOT_SCOPED));
        assertTrue(ex.getMessage().contains("VOLATILITY"), ex.getMessage());
    }

    @Test
    void viewsAreImmutableAndLookupNeverNull() {
        BookAssessments assessments = BookAssessments.of(BULLISH, NOT_SCOPED, NOT_SCOPED, NOT_SCOPED);

        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().remove(H1S));
        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().put(H1S, MIXED));
        assertThrows(UnsupportedOperationException.class, () -> assessments.asList().add(MIXED));
        assertThrows(IllegalArgumentException.class, () -> assessments.of(null));
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(EvidenceDimension.BOOK, assessments.of(horizon).dimension());
        }
    }

    @Test
    void mutatingTheInputMapAfterConstructionHasNoEffect() {
        Map<MarketHorizon, EvidenceAssessment> input = new EnumMap<>(MarketHorizon.class);
        input.put(H1S, BULLISH);
        input.put(H5S, NOT_SCOPED);
        input.put(H15S, NOT_SCOPED);
        input.put(H60S, NOT_SCOPED);
        BookAssessments assessments = new BookAssessments(input);
        input.put(H1S, MIXED);
        input.clear();

        assertSame(BULLISH, assessments.of(H1S));
        assertEquals(4, assessments.asList().size());
    }
}
