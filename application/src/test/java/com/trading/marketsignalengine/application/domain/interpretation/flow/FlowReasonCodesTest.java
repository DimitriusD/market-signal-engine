package com.trading.marketsignalengine.application.domain.interpretation.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityReasonCodes;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The flow taxonomy is typed, well-formed, duplicate-free, deterministic and disjoint from quality codes. */
class FlowReasonCodesTest {

    @Test
    void everyConstantIsListedOnceInPipelineOrder() throws IllegalAccessException {
        List<ReasonCode> declared = new ArrayList<>();
        for (Field field : FlowReasonCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ReasonCode.class) {
                declared.add((ReasonCode) field.get(null));
            }
        }

        assertEquals(10, declared.size());
        assertEquals(new HashSet<>(declared), new HashSet<>(FlowReasonCodes.ALL), "ALL lists every constant");
        assertEquals(FlowReasonCodes.FLOW_WINDOW_MISSING, FlowReasonCodes.ALL.get(0), "pipeline order: missing first");
        assertEquals(FlowReasonCodes.FLOW_NEUTRAL_IMBALANCE, FlowReasonCodes.ALL.get(9), "pipeline order: direction last");
        assertEquals(new HashSet<>(FlowReasonCodes.ALL).size(), FlowReasonCodes.ALL.size(), "no duplicates");
        assertThrows(UnsupportedOperationException.class, () -> FlowReasonCodes.ALL.add(FlowReasonCodes.FLOW_WINDOW_MISSING));
    }

    @Test
    void codesAreUpperSnakeCaseWithFlowPrefixAndDisjointFromQualityCodes() {
        Set<String> quality = Set.of(
                QualityReasonCodes.WINDOW_WARMING_UP.value(), QualityReasonCodes.WINDOW_NOT_COMPUTED.value(),
                QualityReasonCodes.TRADE_FLOW_GROUP_ABSENT.value(), QualityReasonCodes.TRADE_FLOW_CALCULATOR_FAILED.value(),
                QualityReasonCodes.STALE_TRADES.value(), QualityReasonCodes.TRADE_HISTORY_GAP.value(),
                QualityReasonCodes.SOURCE_NO_DATA.value());
        for (ReasonCode code : FlowReasonCodes.ALL) {
            assertTrue(ReasonCode.FORMAT.matcher(code.value()).matches(), code.value());
            assertTrue(code.value().startsWith("FLOW_"), code.value());
            assertTrue(!quality.contains(code.value()), "eligibility reasons are kept, not re-encoded: " + code);
        }
    }
}
