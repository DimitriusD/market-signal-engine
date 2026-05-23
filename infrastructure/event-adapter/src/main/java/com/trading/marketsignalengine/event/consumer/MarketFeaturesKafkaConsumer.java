package com.trading.marketsignalengine.event.consumer;

import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.port.input.EvaluateMarketSignalsUseCase;
import com.trading.marketsignalengine.event.mapper.MarketFeaturesEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketFeaturesKafkaConsumer {

    private final MarketFeaturesEventMapper mapper;
    private final EvaluateMarketSignalsUseCase useCase;

    @KafkaListener(
            topics = "${app.kafka.topics.market-features}",
            groupId = "${app.kafka.consumer.group-id}",
            containerFactory = "marketFeaturesKafkaListenerContainerFactory")
    public void onMessage(String key, MarketFeaturesSnapshotEvent event) {
        if (event == null) {
            log.warn("Received null MarketFeaturesSnapshotEvent, key={}", key);
            return;
        }

        String eventId = safeMetadataField(event, "eventId");
        String exchange = safeMetadataField(event, "exchange");
        String symbol = safeMetadataField(event, "symbol");
        String instrumentId = safeMetadataField(event, "instrumentId");

        log.debug(
                "Received market features event: key={}, eventId={}, exchange={}, symbol={}, instrumentId={}",
                key,
                eventId,
                exchange,
                symbol,
                instrumentId);

        try {
            MarketFeaturesSnapshot domain = mapper.toDomain(event);
            MarketSignalSnapshot snapshot = useCase.evaluate(domain);

            log.info(
                    "Evaluated market signals: sourceFeatureSnapshotId={}, signalSnapshotId={}, symbol={}, instrumentId={}",
                    snapshot.sourceFeatureSnapshotId(),
                    snapshot.signalSnapshotId(),
                    snapshot.symbol(),
                    snapshot.instrumentId());
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to process market features event: key={}, eventId={}, exchange={}, symbol={}, instrumentId={}",
                    key,
                    eventId,
                    exchange,
                    symbol,
                    instrumentId,
                    ex);
            throw ex;
        }
    }

    private static String safeMetadataField(MarketFeaturesSnapshotEvent event, String field) {
        if (event.getMetadata() == null) {
            return null;
        }
        return switch (field) {
            case "eventId" -> event.getMetadata().getEventId();
            case "exchange" -> event.getMetadata().getExchange();
            case "symbol" -> event.getMetadata().getSymbol();
            case "instrumentId" -> event.getMetadata().getInstrumentId();
            default -> null;
        };
    }
}
