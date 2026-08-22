package com.trading.marketsignalengine.event.config;

import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.event.publisher.MarketSignalSnapshotPublisher;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class PublisherConfiguration {

    @Bean
    public MarketSignalSnapshotPublisherPort marketSignalSnapshotPublisher(
            KafkaTemplate<String, MarketSignalSnapshotEvent> marketSignalsKafkaTemplate,
            @Value("${app.kafka.topic.market-signals}") String topic,
            @Value("${app.kafka.publish-timeout-ms:5000}") long publishTimeoutMs) {
        return new MarketSignalSnapshotPublisher(
                marketSignalsKafkaTemplate, topic, Duration.ofMillis(publishTimeoutMs));
    }
}
