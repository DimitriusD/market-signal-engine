package com.trading.marketsignalengine.application.domain.interpretation.assembly;

import static com.trading.marketsignalengine.application.domain.interpretation.assembly.AssemblyFixtures.baseValidities;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Stage 9 validity policy: explicit whole-millisecond durations, exactly four canonical horizon
 * keys, overflow-checked deductions and the guarantee that no policy can ever produce
 * {@code validUntil <= evaluatedAt}.
 */
class InterpretationValidityPolicyTest {

    @Test
    void carriesValuesWithCanonicalHorizonAccess() {
        InterpretationValidityPolicy policy = AssemblyFixtures.VALIDITY_POLICY;

        assertEquals("validity-fixture-v1", policy.policyVersion());
        assertEquals(Duration.ofMillis(400), policy.momentumContinuationBaseValidityOf(H1S));
        assertEquals(Duration.ofMillis(500), policy.momentumContinuationBaseValidityOf(H5S));
        assertEquals(Duration.ofMillis(1_500), policy.momentumContinuationBaseValidityOf(H15S));
        assertEquals(Duration.ofMillis(5_000), policy.momentumContinuationBaseValidityOf(H60S));
        assertEquals(List.of(H1S, H5S, H15S, H60S),
                List.copyOf(policy.momentumContinuationBaseValidity().keySet()), "canonical iteration order");
        assertEquals(Duration.ofMillis(300), policy.noOpportunityBaseValidity());
        assertEquals(Duration.ofMillis(250), policy.blockedBaseValidity());
        assertEquals(Duration.ofMillis(100), policy.publicationSafetyBuffer());
        assertEquals(Duration.ofMillis(50), policy.degradedQualityAdjustment());
        assertEquals(Duration.ofMillis(25), policy.volatileRegimeAdjustment());
        assertThrows(IllegalArgumentException.class, () -> policy.momentumContinuationBaseValidityOf(null));
    }

    @Test
    void rejectsMissingOrPlaceholderVersion() {
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.version = null));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.version = "  "));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.version = "todo"),
                "a placeholder is not a version");
    }

    @Test
    void rejectsBrokenHorizonMap() {
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.base = null));
        Map<MarketHorizon, Duration> missing = baseValidities();
        missing.remove(H15S);
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.base = missing), "missing key");
        Map<MarketHorizon, Duration> nullValue = baseValidities();
        nullValue.put(H5S, null);
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.base = nullValue), "null value");
        Map<MarketHorizon, Duration> nullKey = new HashMap<>(baseValidities());
        nullKey.put(null, Duration.ofMillis(400));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.base = nullKey), "extra null key");
    }

    @Test
    void horizonMapIsDefensivelyCopiedAndImmutable() {
        Map<MarketHorizon, Duration> mutable = baseValidities();
        InterpretationValidityPolicy policy = policy(v -> v.base = mutable);

        mutable.put(H5S, Duration.ofMillis(9_999));
        assertEquals(Duration.ofMillis(500), policy.momentumContinuationBaseValidityOf(H5S), "defensive copy");
        assertThrows(UnsupportedOperationException.class,
                () -> policy.momentumContinuationBaseValidity().put(H5S, Duration.ofMillis(1)));
    }

    @Test
    void rejectsNonPositiveOrSubMillisecondBaseDurations() {
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.noOpportunity = Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.noOpportunity = Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.blocked = Duration.ofNanos(500_000)),
                "sub-millisecond precision");
        Map<MarketHorizon, Duration> subMs = baseValidities();
        subMs.put(H1S, Duration.ofMillis(400).plusNanos(1));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.base = subMs));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.noOpportunity = null));
    }

    @Test
    void rejectsNegativeOrSubMillisecondAdjustments() {
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.buffer = Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.degraded = Duration.ofNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.volatileAdj = null));
        // zero adjustments are legal
        InterpretationValidityPolicy zeroAdjustments = policy(v -> {
            v.buffer = Duration.ZERO;
            v.degraded = Duration.ZERO;
            v.volatileAdj = Duration.ZERO;
        });
        assertEquals(Duration.ZERO, zeroAdjustments.publicationSafetyBuffer());
    }

    @Test
    void rejectsDurationAndDeductionOverflow() {
        // a base duration whose millisecond value does not fit a long
        Map<MarketHorizon, Duration> huge = baseValidities();
        huge.put(H60S, Duration.ofSeconds(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.base = huge), "duration overflow");
        // summed deductions overflow long milliseconds
        assertThrows(IllegalArgumentException.class, () -> policy(v -> {
            v.buffer = Duration.ofMillis(Long.MAX_VALUE);
            v.degraded = Duration.ofMillis(1);
        }), "deduction overflow");
    }

    @Test
    void baseValiditiesMustStrictlyExceedTheirDeductions() {
        // candidate deductions: 100 + 50 + 25 = 175 ms; a base equal to that is rejected
        Map<MarketHorizon, Duration> tooSmall = baseValidities();
        tooSmall.put(H1S, Duration.ofMillis(175));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.base = tooSmall));
        Map<MarketHorizon, Duration> justEnough = baseValidities();
        justEnough.put(H1S, Duration.ofMillis(176));
        assertEquals(Duration.ofMillis(176),
                policy(v -> v.base = justEnough).momentumContinuationBaseValidityOf(H1S));
        // non-candidate bases only need to exceed the publication buffer (100 ms)
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.noOpportunity = Duration.ofMillis(100)));
        assertThrows(IllegalArgumentException.class, () -> policy(v -> v.blocked = Duration.ofMillis(100)));
        assertEquals(Duration.ofMillis(101),
                policy(v -> v.noOpportunity = Duration.ofMillis(101)).noOpportunityBaseValidity());
    }

    // ------------------------------------------------------------------ helper

    private static final class Values {
        String version = "validity-fixture-v1";
        Map<MarketHorizon, Duration> base = baseValidities();
        Duration noOpportunity = Duration.ofMillis(300);
        Duration blocked = Duration.ofMillis(250);
        Duration buffer = Duration.ofMillis(100);
        Duration degraded = Duration.ofMillis(50);
        Duration volatileAdj = Duration.ofMillis(25);
    }

    private static InterpretationValidityPolicy policy(java.util.function.Consumer<Values> customizer) {
        Values v = new Values();
        customizer.accept(v);
        return new InterpretationValidityPolicy(v.version, v.base, v.noOpportunity, v.blocked,
                v.buffer, v.degraded, v.volatileAdj);
    }
}
