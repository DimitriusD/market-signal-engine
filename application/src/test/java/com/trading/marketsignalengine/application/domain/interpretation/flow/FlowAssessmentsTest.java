package com.trading.marketsignalengine.application.domain.interpretation.flow;

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

/** Exactly four FLOW assessments, canonical order, FLOW dimension only, fail-fast lookups, immutable. */
class FlowAssessmentsTest {

    private static final EvidenceAssessment BULLISH = EvidenceAssessment.available(EvidenceDimension.FLOW,
            InterpretationDirection.BULLISH, EvidenceStrength.of("0.4"), List.of(FlowReasonCodes.FLOW_BULLISH_IMBALANCE));
    private static final EvidenceAssessment NEUTRAL = EvidenceAssessment.available(EvidenceDimension.FLOW,
            InterpretationDirection.NEUTRAL, EvidenceStrength.MIN, List.of(FlowReasonCodes.FLOW_NEUTRAL_IMBALANCE));
    private static final EvidenceAssessment MISSING = EvidenceAssessment.unavailable(EvidenceDimension.FLOW,
            List.of(FlowReasonCodes.FLOW_WINDOW_MISSING));

    @Test
    void storesExactlyOnePerHorizonInCanonicalOrderRegardlessOfInputOrder() {
        Map<MarketHorizon, EvidenceAssessment> unordered = new HashMap<>();
        unordered.put(H60S, MISSING);
        unordered.put(H1S, BULLISH);
        unordered.put(H15S, NEUTRAL);
        unordered.put(H5S, BULLISH);

        FlowAssessments assessments = new FlowAssessments(unordered);

        assertEquals(List.of(H1S, H5S, H15S, H60S), new ArrayList<>(assessments.asMap().keySet()));
        assertEquals(List.of(BULLISH, BULLISH, NEUTRAL, MISSING), assessments.asList());
        assertSame(NEUTRAL, assessments.of(H15S));
        assertEquals(assessments, FlowAssessments.of(BULLISH, BULLISH, NEUTRAL, MISSING));
        assertEquals(assessments.hashCode(), FlowAssessments.of(BULLISH, BULLISH, NEUTRAL, MISSING).hashCode());
        assertNotEquals(assessments, FlowAssessments.of(BULLISH, BULLISH, NEUTRAL, NEUTRAL));
    }

    @Test
    void missingHorizonFailsFast() {
        Map<MarketHorizon, EvidenceAssessment> partial = new EnumMap<>(MarketHorizon.class);
        partial.put(H1S, BULLISH);
        partial.put(H5S, BULLISH);
        partial.put(H15S, NEUTRAL);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new FlowAssessments(partial));
        assertTrue(ex.getMessage().contains("H60S"), ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new FlowAssessments(null));
        assertThrows(IllegalArgumentException.class, () -> FlowAssessments.of(BULLISH, null, NEUTRAL, MISSING));
    }

    @Test
    void entriesBeyondTheFourCanonicalKeysAreRejectedNotSilentlyDropped() {
        Map<MarketHorizon, EvidenceAssessment> withNullKey = new HashMap<>();
        withNullKey.put(H1S, BULLISH);
        withNullKey.put(H5S, BULLISH);
        withNullKey.put(H15S, NEUTRAL);
        withNullKey.put(H60S, MISSING);
        withNullKey.put(null, NEUTRAL);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new FlowAssessments(withNullKey));
        assertTrue(ex.getMessage().contains("exactly the four canonical horizons"), ex.getMessage());
    }

    @Test
    void rejectsNonFlowDimension() {
        EvidenceAssessment momentum = EvidenceAssessment.unavailable(EvidenceDimension.MOMENTUM, List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FlowAssessments.of(BULLISH, momentum, NEUTRAL, MISSING));
        assertTrue(ex.getMessage().contains("MOMENTUM"), ex.getMessage());
    }

    @Test
    void viewsAreImmutableAndLookupNeverNull() {
        FlowAssessments assessments = FlowAssessments.of(BULLISH, BULLISH, NEUTRAL, MISSING);

        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().remove(H1S));
        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().put(H1S, NEUTRAL));
        assertThrows(UnsupportedOperationException.class, () -> assessments.asList().add(NEUTRAL));
        assertThrows(IllegalArgumentException.class, () -> assessments.of(null));
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            assertEquals(EvidenceDimension.FLOW, assessments.of(horizon).dimension());
        }
    }

    @Test
    void mutatingTheInputMapAfterConstructionHasNoEffect() {
        Map<MarketHorizon, EvidenceAssessment> input = new EnumMap<>(MarketHorizon.class);
        input.put(H1S, BULLISH);
        input.put(H5S, BULLISH);
        input.put(H15S, NEUTRAL);
        input.put(H60S, MISSING);
        FlowAssessments assessments = new FlowAssessments(input);
        input.put(H60S, NEUTRAL);
        input.clear();

        assertSame(MISSING, assessments.of(H60S));
        assertEquals(4, assessments.asList().size());
    }
}
