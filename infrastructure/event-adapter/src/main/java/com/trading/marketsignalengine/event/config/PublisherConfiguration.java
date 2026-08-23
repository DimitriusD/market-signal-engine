package com.trading.marketsignalengine.event.config;

import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.event.publisher.MarketSignalSnapshotPublisher;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Output publisher wiring with fail-fast startup validation: a blank output topic, a non-positive
 * publish timeout or a broken timeout hierarchy ({@link PublishTimeoutHierarchy}) abort application
 * startup with a clear message instead of surfacing as a runtime publish failure.
 */
@Slf4j
@Configuration
public class PublisherConfiguration {

    @Bean
    public PublishTimeoutHierarchy publishTimeoutHierarchy(
            KafkaProperties kafkaProperties,
            @Value("${app.kafka.publish-timeout-ms:6500}") long publishTimeoutMs) {
        PublishTimeoutHierarchy hierarchy = PublishTimeoutHierarchy
                .from(kafkaProperties.buildProducerProperties(null), publishTimeoutMs)
                .validate();
        log.info("Kafka publish timeout hierarchy: request.timeout.ms={} < delivery.timeout.ms={} < publish-timeout-ms={}",
                hierarchy.requestTimeoutMs(), hierarchy.deliveryTimeoutMs(), hierarchy.publishTimeoutMs());
        return hierarchy;
    }

    @Bean
    public MarketSignalSnapshotPublisherPort marketSignalSnapshotPublisher(
            KafkaTemplate<String, MarketSignalSnapshotEvent> marketSignalsKafkaTemplate,
            PublishTimeoutHierarchy publishTimeoutHierarchy,
            @Value("${app.kafka.topic.market-signals:}") String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException(
                    "app.kafka.topic.market-signals must not be blank (output topic of MarketSignalSnapshotEvent)");
        }
        return new MarketSignalSnapshotPublisher(
                marketSignalsKafkaTemplate, topic, Duration.ofMillis(publishTimeoutHierarchy.publishTimeoutMs()));
    }
}
