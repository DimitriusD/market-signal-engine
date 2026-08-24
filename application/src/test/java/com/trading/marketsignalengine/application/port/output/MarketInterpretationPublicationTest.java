package com.trading.marketsignalengine.application.port.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationAssemblyPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationSnapshotAssembler;
import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.InterpretationValidityPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessmentResolver;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityEligibilityPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityHorizonPolicy;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The transport-timestamp envelope of one published interpretation: non-null valid epoch instants,
 * {@code processedAt >= receivedAt}, and no influence on the snapshot or its deterministic id.
 */
class MarketInterpretationPublicationTest {

    private static final Instant RECEIVED_AT = SignalRuleTestSupport.EVENT_TIME.plusMillis(100);
    private static final Instant PROCESSED_AT = RECEIVED_AT.plusMillis(5);

    @Test
    void carriesSnapshotAndTransportTimestamps() {
        MarketInterpretationSnapshot snapshot = snapshot();

        MarketInterpretationPublication publication =
                new MarketInterpretationPublication(snapshot, RECEIVED_AT, PROCESSED_AT);

        assertEquals(snapshot, publication.snapshot());
        assertEquals(RECEIVED_AT, publication.receivedAt());
        assertEquals(PROCESSED_AT, publication.processedAt());
        // processedAt == receivedAt is legal (a fixed clock reads the same instant twice)
        assertEquals(RECEIVED_AT,
                new MarketInterpretationPublication(snapshot, RECEIVED_AT, RECEIVED_AT).processedAt());
    }

    @Test
    void transportTimestampsNeverChangeTheSnapshotId() {
        MarketInterpretationSnapshot snapshot = snapshot();

        MarketInterpretationPublication early =
                new MarketInterpretationPublication(snapshot, RECEIVED_AT, PROCESSED_AT);
        MarketInterpretationPublication late =
                new MarketInterpretationPublication(snapshot, RECEIVED_AT.plusSeconds(60), RECEIVED_AT.plusSeconds(61));

        assertEquals(early.snapshot().interpretationSnapshotId(), late.snapshot().interpretationSnapshotId());
        assertEquals(snapshot, early.snapshot(), "the publication never mutates the snapshot");
    }

    @Test
    void rejectsMissingOrInvalidComponents() {
        MarketInterpretationSnapshot snapshot = snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> new MarketInterpretationPublication(null, RECEIVED_AT, PROCESSED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketInterpretationPublication(snapshot, null, PROCESSED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketInterpretationPublication(snapshot, RECEIVED_AT, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketInterpretationPublication(snapshot, Instant.EPOCH, PROCESSED_AT),
                "a non-positive epoch timestamp is not a real transport instant");
        assertThrows(IllegalArgumentException.class,
                () -> new MarketInterpretationPublication(snapshot, RECEIVED_AT, RECEIVED_AT.minusMillis(1)),
                "processedAt must not precede receivedAt");
    }

    // ------------------------------------------------------------------ fixture

    /** A real assembled snapshot (the publication wraps only complete domain aggregates). */
    private static MarketInterpretationSnapshot snapshot() {
        HorizonInterpretationPolicy horizonPolicy = new HorizonInterpretationPolicy("horizon-fixture-v1",
                FlowAssessmentPolicy.of("horizon-flow-v1",
                        flowPolicy(MarketHorizon.H1S), flowPolicy(MarketHorizon.H5S),
                        flowPolicy(MarketHorizon.H15S), flowPolicy(MarketHorizon.H60S)),
                MomentumAssessmentPolicy.of("horizon-momentum-v1",
                        momentumPolicy(MarketHorizon.H5S), momentumPolicy(MarketHorizon.H15S),
                        momentumPolicy(MarketHorizon.H60S)),
                VolatilityAssessmentPolicy.of("horizon-volatility-v1",
                        volatilityPolicy(MarketHorizon.H1S), volatilityPolicy(MarketHorizon.H5S),
                        volatilityPolicy(MarketHorizon.H15S), volatilityPolicy(MarketHorizon.H60S)),
                new BookAssessmentPolicy("horizon-book-v1", 5,
                        bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50")));
        EnumMap<MarketHorizon, Duration> base = new EnumMap<>(Map.of(
                MarketHorizon.H1S, Duration.ofMillis(400), MarketHorizon.H5S, Duration.ofMillis(500),
                MarketHorizon.H15S, Duration.ofMillis(1_500), MarketHorizon.H60S, Duration.ofMillis(5_000)));
        MarketInterpretationAssemblyPolicy assemblyPolicy = new MarketInterpretationAssemblyPolicy(
                "mse-interpretation-fixture-v1", "cfg-interpretation-fixture-1",
                new OpportunityInterpretationPolicy("opportunity-fixture-v1",
                        new CrossHorizonInterpretationPolicy("cross-fixture-v1", horizonPolicy), false),
                new InterpretationValidityPolicy("validity-fixture-v1", base,
                        Duration.ofMillis(300), Duration.ofMillis(250),
                        Duration.ofMillis(100), Duration.ofMillis(50), Duration.ofMillis(25)));
        var snapshot = SignalRuleTestSupport.tradableFeaturesBuilder().build();
        var quality = new QualityAssessmentResolver().resolve(snapshot, RECEIVED_AT,
                QualityEligibilityPolicy.of(Duration.ofMillis(2_000), Duration.ofMillis(1_000), true));
        return new MarketInterpretationSnapshotAssembler().assemble(snapshot, quality, assemblyPolicy);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static FlowHorizonPolicy flowPolicy(MarketHorizon horizon) {
        return FlowHorizonPolicy.of(horizon, bd("0.30"), bd("-0.30"), 10, 5, bd("0.5"));
    }

    private static MomentumHorizonPolicy momentumPolicy(MarketHorizon horizon) {
        return MomentumHorizonPolicy.of(horizon, bd("2"), bd("-2"), bd("10"), bd("50"));
    }

    private static VolatilityHorizonPolicy volatilityPolicy(MarketHorizon horizon) {
        return VolatilityHorizonPolicy.of(horizon, bd("2"), bd("8"), bd("15"));
    }
}
