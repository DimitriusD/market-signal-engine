package com.trading.marketsignalengine.event.consumer;

import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.event.mapper.MarketFeaturesSnapshotAvroMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MarketFeaturesKafkaConsumer {

    private final MarketFeaturesHandler marketFeaturesHandler;

    public MarketFeaturesKafkaConsumer(MarketFeaturesHandler marketFeatureHandler) {
        this.marketFeaturesHandler = marketFeatureHandler;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.market-features}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "marketFeaturesKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, MarketFeaturesSnapshotEvent> featuresSnapshotEvent) {
        final MarketFeaturesSnapshotEvent avro = featuresSnapshotEvent.value();
        if (avro == null) {
            return;
        }
        final MarketFeaturesSnapshot features = MarketFeaturesSnapshotAvroMapper.toDomain(avro);
        marketFeaturesHandler.handle(features);
    }
}
