package com.trading.marketsignalengine.event.mapper;

import com.trading.contracts.common.MetadataEvent;
import com.trading.contracts.signal.CrossHorizonAssessmentEvent;
import com.trading.contracts.signal.EvidenceAssessmentEvent;
import com.trading.contracts.signal.FeatureLineageEvent;
import com.trading.contracts.signal.HorizonAssessmentEvent;
import com.trading.contracts.signal.HorizonEligibilityEvent;
import com.trading.contracts.signal.InterpretationLineageEvent;
import com.trading.contracts.signal.InterpretationQualityEvent;
import com.trading.contracts.signal.MarketInterpretationSnapshotEvent;
import com.trading.contracts.signal.MarketOpportunityEvent;
import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.FeatureLineage;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationLineage;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQuality;
import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationPublication;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain → Avro mapping of one {@link MarketInterpretationPublication} to the V2 output contract
 * {@code MarketInterpretationSnapshotEvent}. Pure and lossless: every domain field is transferred
 * exactly — enum taxonomies as {@code name()}, horizons as their contract {@code wireValue()}
 * ({@code 1S}, never {@code H1S}), strengths as {@code BigDecimal.toPlainString()} with an absent
 * strength staying {@code null} (never {@code "0"} or {@code ""}), reason/invalidation codes as
 * their raw values, horizon lists in canonical order. LONG/SHORT and NO_OPPORTUNITY/BLOCKED are
 * published as-is — the contract carries a market interpretation, never a BUY/SELL/NO_TRADE command.
 * No clock, no Spring, no Kafka, no metrics; a missing required value is an
 * {@link AvroMappingException}, never a fabricated {@code ""} / {@code 0} / {@code UNKNOWN}.
 *
 * <p>Metadata: {@code schemaVersion = 2}, {@code eventType = MARKET_INTERPRETATION_SNAPSHOT},
 * {@code sourceStream = market-signal-engine}, {@code eventId = interpretationSnapshotId},
 * {@code exchangeTs = evaluatedAt = event.evaluatedTs} (the market as-of tick), while
 * {@code receivedTs}/{@code processedTs} come from the publication's transport timestamps — the
 * domain snapshot deliberately carries no transport time.
 */
public final class MarketInterpretationSnapshotAvroMapper {

    private static final int SCHEMA_VERSION = 2;
    private static final String EVENT_TYPE = "MARKET_INTERPRETATION_SNAPSHOT";
    private static final String SOURCE_STREAM = "market-signal-engine";

    private MarketInterpretationSnapshotAvroMapper() {
    }

    public static MarketInterpretationSnapshotEvent toAvro(MarketInterpretationPublication publication) {
        if (publication == null) {
            throw new AvroMappingException("MarketInterpretationPublication must not be null");
        }
        MarketInterpretationSnapshot snapshot = required(publication.snapshot(), "snapshot");

        return MarketInterpretationSnapshotEvent.newBuilder()
                .setMetadata(buildMetadata(snapshot, publication))
                .setEvaluatedTs(epochMillis(snapshot.evaluatedAt(), "evaluatedAt"))
                .setValidUntilTs(epochMillis(snapshot.validUntil(), "validUntil"))
                .setQuality(buildQuality(snapshot.interpretationQuality()))
                .setHorizonAssessments(buildHorizonAssessments(snapshot.horizonAssessments()))
                .setCrossHorizonAssessment(buildCrossHorizon(snapshot.crossHorizonAssessment()))
                .setOpportunity(buildOpportunity(snapshot.marketOpportunity()))
                .setFeatureLineage(buildFeatureLineage(snapshot.featureLineage()))
                .setInterpretationLineage(buildInterpretationLineage(snapshot.interpretationLineage()))
                .build();
    }

    // ------------------------------------------------------------------ metadata

    private static MetadataEvent buildMetadata(MarketInterpretationSnapshot snapshot,
                                               MarketInterpretationPublication publication) {
        return MetadataEvent.newBuilder()
                .setSchemaVersion(SCHEMA_VERSION)
                .setEventType(EVENT_TYPE)
                .setExchange(requiredText(snapshot.exchange(), "exchange"))
                .setMarketType(requiredText(snapshot.marketType(), "marketType"))
                .setBase(requiredText(snapshot.base(), "base"))
                .setQuote(requiredText(snapshot.quote(), "quote"))
                .setSymbol(requiredText(snapshot.symbol(), "symbol"))
                .setInstrumentId(requiredText(snapshot.instrumentId(), "instrumentId"))
                .setEventId(requiredText(snapshot.interpretationSnapshotId(), "interpretationSnapshotId"))
                .setSourceStream(SOURCE_STREAM)
                // the market as-of tick, always equal to the root evaluatedTs
                .setExchangeTs(epochMillis(snapshot.evaluatedAt(), "evaluatedAt"))
                .setReceivedTs(epochMillis(publication.receivedAt(), "publication.receivedAt"))
                .setProcessedTs(epochMillis(publication.processedAt(), "publication.processedAt"))
                .build();
    }

    // ------------------------------------------------------------------ quality

    private static InterpretationQualityEvent buildQuality(InterpretationQuality quality) {
        required(quality, "interpretationQuality");
        return InterpretationQualityEvent.newBuilder()
                .setStatus(required(quality.status(), "quality.status").name())
                .setEligibleForTrading(quality.eligibleForTrading())
                .setReasonCodes(reasonCodes(quality.reasonCodes()))
                .build();
    }

    // ------------------------------------------------------------------ horizons

    private static List<HorizonAssessmentEvent> buildHorizonAssessments(List<HorizonAssessment> assessments) {
        required(assessments, "horizonAssessments");
        // the domain aggregate stores the four assessments in canonical order (1S, 5S, 15S, 60S)
        List<HorizonAssessmentEvent> events = new ArrayList<>(assessments.size());
        for (HorizonAssessment assessment : assessments) {
            events.add(buildHorizonAssessment(assessment));
        }
        return events;
    }

    private static HorizonAssessmentEvent buildHorizonAssessment(HorizonAssessment assessment) {
        required(assessment, "horizonAssessment");
        return HorizonAssessmentEvent.newBuilder()
                .setHorizon(wireValue(required(assessment.horizon(), "horizon")))
                .setEligibility(HorizonEligibilityEvent.newBuilder()
                        .setStatus(required(assessment.eligibilityStatus(), "eligibility.status").name())
                        .setReasonCodes(reasonCodes(assessment.eligibility().reasonCodes()))
                        .build())
                .setDirection(required(assessment.direction(), "horizon.direction").name())
                .setEvidenceStrength(strength(assessment.evidenceStrength()))
                .setRegime(regime(assessment.regime()))
                .setEvidenceAssessments(buildEvidenceAssessments(assessment.evidenceAssessments()))
                .setReasonCodes(reasonCodes(assessment.reasonCodes()))
                .build();
    }

    private static List<EvidenceAssessmentEvent> buildEvidenceAssessments(List<EvidenceAssessment> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        List<EvidenceAssessmentEvent> events = new ArrayList<>(evidence.size());
        for (EvidenceAssessment assessment : evidence) {
            events.add(EvidenceAssessmentEvent.newBuilder()
                    .setDimension(required(assessment.dimension(), "evidence.dimension").name())
                    .setStatus(required(assessment.availabilityStatus(), "evidence.status").name())
                    .setDirection(required(assessment.direction(), "evidence.direction").name())
                    .setEvidenceStrength(strength(assessment.evidenceStrength()))
                    .setReasonCodes(reasonCodes(assessment.reasonCodes()))
                    .build());
        }
        return events;
    }

    // ------------------------------------------------------------------ cross-horizon

    private static CrossHorizonAssessmentEvent buildCrossHorizon(CrossHorizonAssessment cross) {
        required(cross, "crossHorizonAssessment");
        return CrossHorizonAssessmentEvent.newBuilder()
                .setAlignment(required(cross.alignment(), "cross.alignment").name())
                .setDirection(required(cross.direction(), "cross.direction").name())
                .setEvidenceStrength(strength(cross.evidenceStrength()))
                .setDominantHorizon(cross.dominantHorizon() == null ? null : wireValue(cross.dominantHorizon()))
                .setParticipatingHorizons(wireValues(cross.participatingHorizons()))
                .setConflictingHorizons(wireValues(cross.conflictingHorizons()))
                .setRegime(regime(cross.regime()))
                .setReasonCodes(reasonCodes(cross.reasonCodes()))
                .build();
    }

    // ------------------------------------------------------------------ opportunity

    private static MarketOpportunityEvent buildOpportunity(MarketOpportunity opportunity) {
        required(opportunity, "marketOpportunity");
        // LONG/SHORT and NO_OPPORTUNITY/BLOCKED are published verbatim: this is a market
        // interpretation, never a BUY/SELL or NO_TRADE command.
        return MarketOpportunityEvent.newBuilder()
                .setStatus(required(opportunity.status(), "opportunity.status").name())
                .setType(required(opportunity.type(), "opportunity.type").name())
                .setSide(required(opportunity.side(), "opportunity.side").name())
                .setSetupHorizon(opportunity.setupHorizon() == null ? null : wireValue(opportunity.setupHorizon()))
                .setEvidenceStrength(strength(opportunity.evidenceStrength()))
                .setReasonCodes(reasonCodes(opportunity.reasonCodes()))
                .setInvalidationCodes(reasonCodes(opportunity.invalidationCodes()))
                .build();
    }

    // ------------------------------------------------------------------ lineage

    private static FeatureLineageEvent buildFeatureLineage(FeatureLineage lineage) {
        required(lineage, "featureLineage");
        return FeatureLineageEvent.newBuilder()
                .setSourceFeatureEventId(requiredText(lineage.sourceFeatureEventId(), "sourceFeatureEventId"))
                .setSourceFeatureSchemaVersion(lineage.sourceFeatureSchemaVersion())
                .setSourceFeatureSetVersion(requiredText(lineage.sourceFeatureSetVersion(), "sourceFeatureSetVersion"))
                .setSourceFeatureConfigHash(requiredText(lineage.sourceFeatureConfigHash(), "sourceFeatureConfigHash"))
                .setSourceEvaluationTs(epochMillis(lineage.sourceEvaluationAt(), "sourceEvaluationAt"))
                .setSourceComputedTs(epochMillis(lineage.sourceComputedAt(), "sourceComputedAt"))
                .setSourceTriggerSource(requiredText(lineage.sourceTriggerSource(), "sourceTriggerSource"))
                .build();
    }

    private static InterpretationLineageEvent buildInterpretationLineage(InterpretationLineage lineage) {
        required(lineage, "interpretationLineage");
        return InterpretationLineageEvent.newBuilder()
                .setInterpretationVersion(requiredText(lineage.interpretationVersion(), "interpretationVersion"))
                .setInterpretationConfigHash(
                        requiredText(lineage.interpretationConfigHash(), "interpretationConfigHash"))
                .build();
    }

    // ------------------------------------------------------------------ helpers

    /** The contract horizon identifier ({@code 1S}, {@code 5S}, ...) — never the enum name. */
    private static String wireValue(MarketHorizon horizon) {
        return horizon.wireValue();
    }

    private static List<String> wireValues(List<MarketHorizon> horizons) {
        if (horizons == null || horizons.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(horizons.size());
        for (MarketHorizon horizon : horizons) {
            values.add(wireValue(required(horizon, "horizon list element")));
        }
        return values;
    }

    /** Absent strength stays {@code null} on the wire — never {@code "0"} as a placeholder. */
    private static String strength(EvidenceStrength strength) {
        return strength == null ? null : strength.toPlainString();
    }

    private static String regime(MarketRegime regime) {
        return regime == null ? null : regime.name();
    }

    private static List<String> reasonCodes(List<ReasonCode> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(codes.size());
        for (ReasonCode code : codes) {
            values.add(required(code, "reason code").value());
        }
        return values;
    }

    private static long epochMillis(Instant instant, String fieldName) {
        if (instant == null) {
            throw new AvroMappingException(fieldName + " must not be null");
        }
        return instant.toEpochMilli();
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AvroMappingException(fieldName + " must not be blank");
        }
        return value;
    }

    private static <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new AvroMappingException(fieldName + " must not be null");
        }
        return value;
    }
}
