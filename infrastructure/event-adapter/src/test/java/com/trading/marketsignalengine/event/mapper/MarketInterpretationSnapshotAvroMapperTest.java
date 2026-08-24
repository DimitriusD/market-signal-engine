package com.trading.marketsignalengine.event.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.contracts.signal.CrossHorizonAssessmentEvent;
import com.trading.contracts.signal.EvidenceAssessmentEvent;
import com.trading.contracts.signal.HorizonAssessmentEvent;
import com.trading.contracts.signal.MarketInterpretationSnapshotEvent;
import com.trading.contracts.signal.MarketOpportunityEvent;
import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.FeatureLineage;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationLineage;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQuality;
import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunitySide;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityType;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationPublication;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;

/**
 * Exact, lossless V2 mapping: metadata constants and transport timestamps, canonical wire horizons
 * ({@code 1S/5S/15S/60S}, never enum names), plain-string decimals with honest nulls, verbatim
 * reason/invalidation codes and lineage, every opportunity/quality/cross state — plus fail-fast on
 * a missing publication and an Avro binary round-trip.
 */
class MarketInterpretationSnapshotAvroMapperTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant VALID_UNTIL = EVALUATED_AT.plusMillis(1_750);
    private static final Instant COMPUTED_AT = EVALUATED_AT.plusMillis(25);
    private static final Instant RECEIVED_AT = EVALUATED_AT.plusMillis(100);
    private static final Instant PROCESSED_AT = EVALUATED_AT.plusMillis(105);

    // ------------------------------------------------------------------ full exact mapping

    @Test
    void candidateLongMapsEveryFieldExactly() {
        MarketInterpretationSnapshot snapshot = candidateSnapshot(InterpretationDirection.BULLISH, OpportunitySide.LONG);

        MarketInterpretationSnapshotEvent event = MarketInterpretationSnapshotAvroMapper.toAvro(
                new MarketInterpretationPublication(snapshot, RECEIVED_AT, PROCESSED_AT));

        // metadata: constants, identity, deterministic id, transport timestamps
        var metadata = event.getMetadata();
        assertEquals(2, metadata.getSchemaVersion());
        assertEquals("MARKET_INTERPRETATION_SNAPSHOT", metadata.getEventType());
        assertEquals("market-signal-engine", metadata.getSourceStream());
        assertEquals("binance", metadata.getExchange());
        assertEquals("spot", metadata.getMarketType());
        assertEquals("BTC", metadata.getBase());
        assertEquals("USDT", metadata.getQuote());
        assertEquals("BTCUSDT", metadata.getSymbol());
        assertEquals("binance:spot:BTCUSDT", metadata.getInstrumentId());
        assertEquals(snapshot.interpretationSnapshotId(), metadata.getEventId());
        assertEquals(EVALUATED_AT.toEpochMilli(), metadata.getExchangeTs());
        assertEquals(RECEIVED_AT.toEpochMilli(), metadata.getReceivedTs());
        assertEquals(PROCESSED_AT.toEpochMilli(), metadata.getProcessedTs());

        // root timestamps: exchangeTs == evaluatedTs (market as-of), validity carried exactly
        assertEquals(EVALUATED_AT.toEpochMilli(), event.getEvaluatedTs());
        assertEquals(metadata.getExchangeTs(), event.getEvaluatedTs());
        assertEquals(VALID_UNTIL.toEpochMilli(), event.getValidUntilTs());

        // quality
        assertEquals("OK", event.getQuality().getStatus());
        assertTrue(event.getQuality().getEligibleForTrading());
        assertEquals(List.of(), event.getQuality().getReasonCodes());

        // horizons: canonical wire order, per-horizon eligibility/direction/strength/regime/evidence
        List<HorizonAssessmentEvent> horizons = event.getHorizonAssessments();
        assertEquals(List.of("1S", "5S", "15S", "60S"),
                horizons.stream().map(HorizonAssessmentEvent::getHorizon).toList());
        HorizonAssessmentEvent h1s = horizons.get(0);
        assertEquals("ELIGIBLE", h1s.getEligibility().getStatus());
        assertEquals(List.of(), h1s.getEligibility().getReasonCodes());
        assertEquals("BULLISH", h1s.getDirection());
        assertEquals("0.5", h1s.getEvidenceStrength(), "0.50 normalises to the plain string 0.5");
        assertEquals("TRENDING", h1s.getRegime());
        assertEquals(List.of("HORIZON_DIRECTION_FROM_FLOW"), h1s.getReasonCodes());
        List<EvidenceAssessmentEvent> h1Evidence = h1s.getEvidenceAssessments();
        assertEquals(2, h1Evidence.size());
        assertEquals("FLOW", h1Evidence.get(0).getDimension());
        assertEquals("AVAILABLE", h1Evidence.get(0).getStatus());
        assertEquals("BULLISH", h1Evidence.get(0).getDirection());
        assertEquals("0.6", h1Evidence.get(0).getEvidenceStrength());
        assertEquals(List.of("FLOW_IMBALANCE_BULLISH"), h1Evidence.get(0).getReasonCodes());
        assertEquals("MOMENTUM", h1Evidence.get(1).getDimension());
        assertEquals("UNAVAILABLE", h1Evidence.get(1).getStatus());
        assertEquals("UNKNOWN", h1Evidence.get(1).getDirection());
        assertNull(h1Evidence.get(1).getEvidenceStrength(), "absent strength stays null, never \"0\"");
        HorizonAssessmentEvent h60s = horizons.get(3);
        assertEquals("60S", h60s.getHorizon());
        assertEquals("0.6", h60s.getEvidenceStrength());

        // cross-horizon
        CrossHorizonAssessmentEvent cross = event.getCrossHorizonAssessment();
        assertEquals("ALIGNED_BULLISH", cross.getAlignment());
        assertEquals("BULLISH", cross.getDirection());
        assertEquals("0.6", cross.getEvidenceStrength());
        assertEquals("60S", cross.getDominantHorizon());
        assertEquals(List.of("1S", "5S", "15S", "60S"), cross.getParticipatingHorizons());
        assertEquals(List.of(), cross.getConflictingHorizons());
        assertEquals("TRENDING", cross.getRegime());
        assertEquals(List.of("CROSS_HORIZON_ALIGNED_BULLISH"), cross.getReasonCodes());

        // opportunity: LONG stays LONG — an interpretation, never BUY/SELL
        MarketOpportunityEvent opportunity = event.getOpportunity();
        assertEquals("CANDIDATE", opportunity.getStatus());
        assertEquals("MOMENTUM_CONTINUATION", opportunity.getType());
        assertEquals("LONG", opportunity.getSide());
        assertEquals("5S", opportunity.getSetupHorizon());
        assertEquals("0.6", opportunity.getEvidenceStrength());
        assertEquals(List.of("OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE", "OPPORTUNITY_LONG"),
                opportunity.getReasonCodes());
        assertEquals(List.of("OPPORTUNITY_INVALIDATE_QUALITY", "OPPORTUNITY_INVALIDATE_STRENGTH"),
                opportunity.getInvalidationCodes());

        // lineage: verbatim
        var featureLineage = event.getFeatureLineage();
        assertEquals("feat-1", featureLineage.getSourceFeatureEventId());
        assertEquals(1, featureLineage.getSourceFeatureSchemaVersion());
        assertEquals("mfs-features-v2", featureLineage.getSourceFeatureSetVersion());
        assertEquals("cfg-feat-1", featureLineage.getSourceFeatureConfigHash());
        assertEquals(EVALUATED_AT.toEpochMilli(), featureLineage.getSourceEvaluationTs());
        assertEquals(COMPUTED_AT.toEpochMilli(), featureLineage.getSourceComputedTs());
        assertEquals("TRADE", featureLineage.getSourceTriggerSource());
        assertEquals("mse-interpretation-v1", event.getInterpretationLineage().getInterpretationVersion());
        assertEquals("cfg-int-1", event.getInterpretationLineage().getInterpretationConfigHash());
    }

    @Test
    void candidateShortMapsSymmetrically() {
        MarketInterpretationSnapshot snapshot =
                candidateSnapshot(InterpretationDirection.BEARISH, OpportunitySide.SHORT);

        MarketInterpretationSnapshotEvent event = MarketInterpretationSnapshotAvroMapper.toAvro(
                new MarketInterpretationPublication(snapshot, RECEIVED_AT, PROCESSED_AT));

        assertEquals("ALIGNED_BEARISH", event.getCrossHorizonAssessment().getAlignment());
        assertEquals("CANDIDATE", event.getOpportunity().getStatus());
        assertEquals("SHORT", event.getOpportunity().getSide());
        assertEquals("MOMENTUM_CONTINUATION", event.getOpportunity().getType());
        assertEquals("5S", event.getOpportunity().getSetupHorizon());
    }

    @Test
    void enumNamesNeverLeakAsWireHorizons() {
        MarketInterpretationSnapshotEvent event = MarketInterpretationSnapshotAvroMapper.toAvro(
                new MarketInterpretationPublication(
                        candidateSnapshot(InterpretationDirection.BULLISH, OpportunitySide.LONG),
                        RECEIVED_AT, PROCESSED_AT));

        List<String> allHorizonValues = new java.util.ArrayList<>();
        event.getHorizonAssessments().forEach(h -> allHorizonValues.add(h.getHorizon()));
        allHorizonValues.addAll(event.getCrossHorizonAssessment().getParticipatingHorizons());
        allHorizonValues.add(event.getCrossHorizonAssessment().getDominantHorizon());
        allHorizonValues.add(event.getOpportunity().getSetupHorizon());
        for (String value : allHorizonValues) {
            assertFalse(value.startsWith("H"), "horizon must be the wire value, got " + value);
        }
    }

    // ------------------------------------------------------------------ negative / blocked / cross states

    @Test
    void noOpportunityMapsWithHonestNulls() {
        MarketInterpretationSnapshot snapshot = noOpportunitySnapshot();

        MarketInterpretationSnapshotEvent event = MarketInterpretationSnapshotAvroMapper.toAvro(
                new MarketInterpretationPublication(snapshot, RECEIVED_AT, PROCESSED_AT));

        assertEquals("DEGRADED", event.getQuality().getStatus());
        assertTrue(event.getQuality().getEligibleForTrading());
        assertEquals(List.of("HORIZONS_PARTIALLY_ELIGIBLE"), event.getQuality().getReasonCodes());
        assertEquals("NO_OPPORTUNITY", event.getOpportunity().getStatus());
        assertEquals("NONE", event.getOpportunity().getType());
        assertEquals("NONE", event.getOpportunity().getSide());
        assertNull(event.getOpportunity().getSetupHorizon());
        assertNull(event.getOpportunity().getEvidenceStrength());
        assertEquals(List.of(), event.getOpportunity().getInvalidationCodes());
        // NEUTRAL cross with the two short horizons participating
        assertEquals("NEUTRAL", event.getCrossHorizonAssessment().getAlignment());
        assertEquals(List.of("1S", "5S"), event.getCrossHorizonAssessment().getParticipatingHorizons());
        assertNull(event.getCrossHorizonAssessment().getDominantHorizon());
        assertEquals("0", event.getCrossHorizonAssessment().getEvidenceStrength(),
                "a real computed zero strength is \"0\", not null");
        // non-eligible horizons are listed with explicit status, null strength and null regime
        HorizonAssessmentEvent h15s = event.getHorizonAssessments().get(2);
        assertEquals("15S", h15s.getHorizon());
        assertEquals("UNTRUSTED", h15s.getEligibility().getStatus());
        assertEquals(List.of("TRADE_HISTORY_GAP"), h15s.getEligibility().getReasonCodes());
        assertEquals("UNKNOWN", h15s.getDirection());
        assertNull(h15s.getEvidenceStrength());
        assertNull(h15s.getRegime());
    }

    @Test
    void blockedMapsWithQualityGateAndNoDirectionalContent() {
        MarketInterpretationSnapshot snapshot = blockedSnapshot();

        MarketInterpretationSnapshotEvent event = MarketInterpretationSnapshotAvroMapper.toAvro(
                new MarketInterpretationPublication(snapshot, RECEIVED_AT, PROCESSED_AT));

        assertEquals("BLOCKED", event.getQuality().getStatus());
        assertFalse(event.getQuality().getEligibleForTrading());
        assertEquals(List.of("SOURCE_QUALITY_UNSAFE"), event.getQuality().getReasonCodes());
        assertEquals("BLOCKED", event.getOpportunity().getStatus());
        assertEquals("NONE", event.getOpportunity().getSide(), "never converted into NO_TRADE semantics");
        assertEquals(List.of("OPPORTUNITY_BLOCKED_BY_QUALITY", "SOURCE_QUALITY_UNSAFE"),
                event.getOpportunity().getReasonCodes());
        assertEquals("INSUFFICIENT_DATA", event.getCrossHorizonAssessment().getAlignment());
        assertEquals(List.of(), event.getCrossHorizonAssessment().getParticipatingHorizons());
        assertNull(event.getCrossHorizonAssessment().getEvidenceStrength());
        assertNull(event.getCrossHorizonAssessment().getRegime());
    }

    @Test
    void conflictingCrossStateMapsConflictListInCanonicalOrder() {
        MarketInterpretationSnapshot snapshot = conflictingSnapshot();

        CrossHorizonAssessmentEvent cross = MarketInterpretationSnapshotAvroMapper.toAvro(
                new MarketInterpretationPublication(snapshot, RECEIVED_AT, PROCESSED_AT))
                .getCrossHorizonAssessment();

        assertEquals("CONFLICTING", cross.getAlignment());
        assertEquals("MIXED", cross.getDirection());
        assertNull(cross.getEvidenceStrength());
        assertEquals("60S", cross.getDominantHorizon());
        assertEquals(List.of("5S", "15S", "60S"), cross.getParticipatingHorizons());
        assertEquals(List.of("5S"), cross.getConflictingHorizons());
        assertEquals("VOLATILE", cross.getRegime());
    }

    @Test
    void partiallyAlignedCrossStateMapsDominantDirection() {
        MarketInterpretationSnapshot snapshot = partiallyAlignedSnapshot();

        CrossHorizonAssessmentEvent cross = MarketInterpretationSnapshotAvroMapper.toAvro(
                new MarketInterpretationPublication(snapshot, RECEIVED_AT, PROCESSED_AT))
                .getCrossHorizonAssessment();

        assertEquals("PARTIALLY_ALIGNED", cross.getAlignment());
        assertEquals("BULLISH", cross.getDirection());
        assertEquals(List.of("15S", "60S"), cross.getParticipatingHorizons());
        assertEquals(List.of(), cross.getConflictingHorizons());
    }

    // ------------------------------------------------------------------ fail-fast + round-trip

    @Test
    void nullPublicationAndInvalidTransportTimestampsFailFast() {
        assertThrows(AvroMappingException.class, () -> MarketInterpretationSnapshotAvroMapper.toAvro(null));
        MarketInterpretationSnapshot snapshot = candidateSnapshot(InterpretationDirection.BULLISH, OpportunitySide.LONG);
        // invalid transport timestamps never reach the mapper — the publication itself rejects them
        assertThrows(IllegalArgumentException.class,
                () -> new MarketInterpretationPublication(snapshot, null, PROCESSED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketInterpretationPublication(snapshot, RECEIVED_AT, RECEIVED_AT.minusMillis(1)));
    }

    @Test
    void avroBinaryRoundTripPreservesTheEvent() throws Exception {
        MarketInterpretationSnapshotEvent event = MarketInterpretationSnapshotAvroMapper.toAvro(
                new MarketInterpretationPublication(
                        candidateSnapshot(InterpretationDirection.BULLISH, OpportunitySide.LONG),
                        RECEIVED_AT, PROCESSED_AT));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        new SpecificDatumWriter<>(MarketInterpretationSnapshotEvent.class).write(event, encoder);
        encoder.flush();
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(out.toByteArray(), null);
        MarketInterpretationSnapshotEvent back =
                new SpecificDatumReader<>(MarketInterpretationSnapshotEvent.class).read(null, decoder);

        assertEquals(event, back, "binary round-trip must be lossless");
        assertEquals("0.5", back.getHorizonAssessments().get(0).getEvidenceStrength());
    }

    // ------------------------------------------------------------------ domain fixtures

    private static MarketInterpretationSnapshot.Builder baseBuilder() {
        return MarketInterpretationSnapshot.builder()
                .exchange("binance").marketType("spot").base("BTC").quote("USDT")
                .symbol("BTCUSDT").instrumentId("binance:spot:BTCUSDT")
                .evaluatedAt(EVALUATED_AT).validUntil(VALID_UNTIL)
                .featureLineage(new FeatureLineage("feat-1", 1, "mfs-features-v2", "cfg-feat-1",
                        EVALUATED_AT, COMPUTED_AT, "TRADE"))
                .interpretationLineage(new InterpretationLineage("mse-interpretation-v1", "cfg-int-1"));
    }

    private static MarketInterpretationSnapshot candidateSnapshot(InterpretationDirection direction,
                                                                  OpportunitySide side) {
        EvidenceStrength strength = EvidenceStrength.of("0.6");
        HorizonAssessment h1s = HorizonAssessment.eligible(MarketHorizon.H1S, direction,
                EvidenceStrength.of("0.50"), MarketRegime.TRENDING,
                List.of(EvidenceAssessment.available(EvidenceDimension.FLOW, direction, strength,
                                List.of(rc("FLOW_IMBALANCE_BULLISH"))),
                        EvidenceAssessment.unavailable(EvidenceDimension.MOMENTUM, List.of())),
                List.of(rc("HORIZON_DIRECTION_FROM_FLOW")));
        HorizonAssessment h5s = eligibleHorizon(MarketHorizon.H5S, direction, strength);
        HorizonAssessment h15s = eligibleHorizon(MarketHorizon.H15S, direction, strength);
        HorizonAssessment h60s = eligibleHorizon(MarketHorizon.H60S, direction, strength);
        List<MarketHorizon> all = List.of(MarketHorizon.H1S, MarketHorizon.H5S, MarketHorizon.H15S, MarketHorizon.H60S);
        CrossHorizonAssessment cross = direction == InterpretationDirection.BULLISH
                ? CrossHorizonAssessment.alignedBullish(strength, MarketHorizon.H60S, all, MarketRegime.TRENDING,
                        List.of(rc("CROSS_HORIZON_ALIGNED_BULLISH")))
                : CrossHorizonAssessment.alignedBearish(strength, MarketHorizon.H60S, all, MarketRegime.TRENDING,
                        List.of(rc("CROSS_HORIZON_ALIGNED_BEARISH")));
        return baseBuilder()
                .interpretationQuality(InterpretationQuality.ok(List.of()))
                .horizonAssessments(List.of(h1s, h5s, h15s, h60s))
                .crossHorizonAssessment(cross)
                .marketOpportunity(MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION, side,
                        MarketHorizon.H5S, strength,
                        List.of(rc("OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE"),
                                side == OpportunitySide.LONG ? rc("OPPORTUNITY_LONG") : rc("OPPORTUNITY_SHORT")),
                        List.of(rc("OPPORTUNITY_INVALIDATE_QUALITY"), rc("OPPORTUNITY_INVALIDATE_STRENGTH"))))
                .build();
    }

    private static MarketInterpretationSnapshot noOpportunitySnapshot() {
        HorizonAssessment h1s = HorizonAssessment.eligible(MarketHorizon.H1S, InterpretationDirection.NEUTRAL,
                EvidenceStrength.MIN, MarketRegime.RANGING, List.of(), List.of());
        HorizonAssessment h5s = HorizonAssessment.eligible(MarketHorizon.H5S, InterpretationDirection.NEUTRAL,
                EvidenceStrength.MIN, MarketRegime.RANGING, List.of(), List.of());
        HorizonAssessment h15s = HorizonAssessment.untrusted(MarketHorizon.H15S, List.of(rc("TRADE_HISTORY_GAP")));
        HorizonAssessment h60s = HorizonAssessment.untrusted(MarketHorizon.H60S, List.of(rc("TRADE_HISTORY_GAP")));
        return baseBuilder()
                .interpretationQuality(InterpretationQuality.degraded(true, List.of(rc("HORIZONS_PARTIALLY_ELIGIBLE"))))
                .horizonAssessments(List.of(h1s, h5s, h15s, h60s))
                .crossHorizonAssessment(CrossHorizonAssessment.neutral(EvidenceStrength.MIN,
                        List.of(MarketHorizon.H1S, MarketHorizon.H5S), MarketRegime.RANGING,
                        List.of(rc("CROSS_HORIZON_NEUTRAL"))))
                .marketOpportunity(MarketOpportunity.noOpportunity(List.of(rc("OPPORTUNITY_NO_OPPORTUNITY"))))
                .build();
    }

    private static MarketInterpretationSnapshot blockedSnapshot() {
        return baseBuilder()
                .interpretationQuality(InterpretationQuality.blocked(List.of(rc("SOURCE_QUALITY_UNSAFE"))))
                .horizonAssessments(List.of(
                        HorizonAssessment.unavailable(MarketHorizon.H1S, List.of(rc("SOURCE_QUALITY_UNSAFE"))),
                        HorizonAssessment.unavailable(MarketHorizon.H5S, List.of(rc("SOURCE_QUALITY_UNSAFE"))),
                        HorizonAssessment.unavailable(MarketHorizon.H15S, List.of(rc("SOURCE_QUALITY_UNSAFE"))),
                        HorizonAssessment.unavailable(MarketHorizon.H60S, List.of(rc("SOURCE_QUALITY_UNSAFE")))))
                .crossHorizonAssessment(CrossHorizonAssessment.insufficientData(List.of(),
                        List.of(rc("CROSS_HORIZON_INSUFFICIENT_DATA"))))
                .marketOpportunity(MarketOpportunity.blocked(
                        List.of(rc("OPPORTUNITY_BLOCKED_BY_QUALITY"), rc("SOURCE_QUALITY_UNSAFE"))))
                .build();
    }

    private static MarketInterpretationSnapshot conflictingSnapshot() {
        EvidenceStrength strength = EvidenceStrength.of("0.6");
        return baseBuilder()
                .interpretationQuality(InterpretationQuality.degraded(true, List.of(rc("HORIZONS_PARTIALLY_ELIGIBLE"))))
                .horizonAssessments(List.of(
                        HorizonAssessment.unavailable(MarketHorizon.H1S, List.of()),
                        eligibleHorizon(MarketHorizon.H5S, InterpretationDirection.BEARISH, strength),
                        eligibleHorizon(MarketHorizon.H15S, InterpretationDirection.BULLISH, strength),
                        eligibleHorizon(MarketHorizon.H60S, InterpretationDirection.BULLISH, strength)))
                .crossHorizonAssessment(CrossHorizonAssessment.conflicting(null, MarketHorizon.H60S,
                        List.of(MarketHorizon.H5S, MarketHorizon.H15S, MarketHorizon.H60S),
                        List.of(MarketHorizon.H5S), MarketRegime.VOLATILE,
                        List.of(rc("CROSS_HORIZON_CONFLICTING"))))
                .marketOpportunity(MarketOpportunity.noOpportunity(
                        List.of(rc("OPPORTUNITY_NO_OPPORTUNITY"), rc("OPPORTUNITY_CROSS_HORIZON_CONFLICT"))))
                .build();
    }

    private static MarketInterpretationSnapshot partiallyAlignedSnapshot() {
        EvidenceStrength strength = EvidenceStrength.of("0.6");
        return baseBuilder()
                .interpretationQuality(InterpretationQuality.degraded(true, List.of(rc("HORIZONS_PARTIALLY_ELIGIBLE"))))
                .horizonAssessments(List.of(
                        HorizonAssessment.unavailable(MarketHorizon.H1S, List.of()),
                        HorizonAssessment.unavailable(MarketHorizon.H5S, List.of()),
                        eligibleHorizon(MarketHorizon.H15S, InterpretationDirection.BULLISH, strength),
                        eligibleHorizon(MarketHorizon.H60S, InterpretationDirection.BULLISH, strength)))
                .crossHorizonAssessment(CrossHorizonAssessment.partiallyAligned(InterpretationDirection.BULLISH,
                        strength, MarketHorizon.H60S, List.of(MarketHorizon.H15S, MarketHorizon.H60S),
                        MarketRegime.TRENDING, List.of(rc("CROSS_HORIZON_PARTIALLY_ALIGNED"))))
                .marketOpportunity(MarketOpportunity.noOpportunity(
                        List.of(rc("OPPORTUNITY_NO_OPPORTUNITY"), rc("OPPORTUNITY_CROSS_HORIZON_PARTIAL"))))
                .build();
    }

    private static HorizonAssessment eligibleHorizon(MarketHorizon horizon, InterpretationDirection direction,
                                                     EvidenceStrength strength) {
        return HorizonAssessment.eligible(horizon, direction, strength, MarketRegime.TRENDING,
                List.of(EvidenceAssessment.available(EvidenceDimension.FLOW, direction, strength, List.of())),
                List.of());
    }

    private static ReasonCode rc(String value) {
        return ReasonCode.of(value);
    }
}
