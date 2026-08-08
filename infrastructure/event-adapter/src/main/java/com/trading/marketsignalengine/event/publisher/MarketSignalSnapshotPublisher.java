package com.trading.marketsignalengine.event.publisher;

import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.event.mapper.MarketSignalSnapshotAvroMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
public final class MarketSignalSnapshotPublisher implements MarketSignalSnapshotPublisherPort {

    private final KafkaTemplate<String, MarketSignalSnapshotEvent> kafkaTemplate;
    private final String topic;

    public MarketSignalSnapshotPublisher(
            KafkaTemplate<String, MarketSignalSnapshotEvent> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(MarketSignalSnapshot snapshot) {
        if (snapshot == null) {
            log.warn("Skipping null market signal snapshot publish");
            return;
        }

        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(snapshot);
        String key = resolveKey(snapshot);

        try {
            kafkaTemplate.send(topic, key, event).join();

            log.info(
                    "Published market signal snapshot: topic={}, key={}, signalSnapshotId={}, sourceFeatureSnapshotId={}, marketBias={}, riskLevel={}, setupSide={}, setupType={}, ttlMs={}, validUntil={}",
                    topic,
                    key,
                    snapshot.signalSnapshotId(),
                    snapshot.sourceFeatureSnapshotId(),
                    snapshot.marketBias(),
                    snapshot.riskLevel(),
                    snapshot.setup() != null ? snapshot.setup().side() : null,
                    snapshot.setup() != null ? snapshot.setup().type() : null,
                    snapshot.ttlMs(),
                    snapshot.validUntil());
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to publish market signal snapshot: topic={}, key={}, signalSnapshotId={}, sourceFeatureSnapshotId={}",
                    topic,
                    key,
                    snapshot.signalSnapshotId(),
                    snapshot.sourceFeatureSnapshotId(),
                    ex);
            throw ex;
        }
    }

    private static String resolveKey(MarketSignalSnapshot snapshot) {
        if (snapshot.instrumentId() != null && !snapshot.instrumentId().isBlank()) {
            return snapshot.instrumentId();
        }
        return nz(snapshot.exchange()) + ":" + nz(snapshot.symbol());
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
