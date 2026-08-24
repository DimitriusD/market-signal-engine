package com.trading.marketsignalengine.event.config;

import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.contracts.signal.MarketInterpretationSnapshotEvent;
import com.trading.marketsignalengine.application.domain.validation.InvalidMarketFeaturesSnapshotException;
import com.trading.marketsignalengine.event.mapper.AvroMappingException;
import com.trading.marketsignalengine.event.metrics.DeadLetterMetrics;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka wiring. The listener container factory is built through Spring Boot's
 * {@link ConcurrentKafkaListenerContainerFactoryConfigurer}, so every {@code spring.kafka.listener.*}
 * property (concurrency, ack-mode, poll-timeout, auto-startup, idle events, ...) applies exactly as
 * documented by Boot instead of being silently ignored by a hand-assembled factory. Error handling:
 * bounded retries with fixed back-off, then dead-letter to {@code <topic>.DLT}; mapping/contract
 * failures are non-retryable and go straight to the DLT. Every recovered (dead-lettered) record is
 * counted via {@link DeadLetterMetrics}.
 */
@Configuration
public class KafkaConfiguration {

    private static Map<String, Object> avroConsumerProps(
            KafkaProperties kafkaProperties, String schemaRegistryUrl) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return props;
    }

    private static Map<String, Object> avroProducerProps(
            KafkaProperties kafkaProperties, String schemaRegistryUrl) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        // Explicit durability: idempotent producer (no duplicates from producer-internal retries,
        // exactly-once ordering per partition) and acks=all. Set in code so no profile can silently
        // weaken them; app-level duplicates after an ambiguous publish timeout remain possible
        // (at-least-once) and are deduplicated downstream on the deterministic interpretationSnapshotId.
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    @Bean
    public ProducerFactory<String, Object> dltProducerFactory(
            KafkaProperties kafkaProperties,
            @Value("${app.kafka.schema-registry.url:http://localhost:8081}") String schemaRegistryUrl) {
        return new DefaultKafkaProducerFactory<>(avroProducerProps(kafkaProperties, schemaRegistryUrl));
    }

    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate(ProducerFactory<String, Object> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    /**
     * Listener error handling. {@code FixedBackOff(backoffMs, maxAttempts)}: {@code maxAttempts} is the
     * number of <em>retries after the first delivery</em>, so a record is delivered
     * {@code maxAttempts + 1} times in total before it is dead-lettered ({@code app.kafka.retry.max-attempts=3}
     * → 4 publisher attempts). {@link SignalPublishException} (bounded publish failure/timeout) is
     * retryable; {@link AvroMappingException} and {@link InvalidMarketFeaturesSnapshotException} are
     * contract errors and go straight to {@code <topic>.DLT} without retry. Because the input offset is
     * only committed after a successful publish, the flow is at-least-once end to end.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> dltKafkaTemplate,
            DeadLetterMetrics deadLetterMetrics,
            @Value("${app.kafka.retry.backoff-ms:1000}") long retryBackoffMs,
            @Value("${app.kafka.retry.max-attempts:3}") long retryMaxAttempts) {
        var recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate, (message, ex) -> new TopicPartition(message.topic() + ".DLT", -1));

        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(retryBackoffMs, retryMaxAttempts));
        errorHandler.addNotRetryableExceptions(
                AvroMappingException.class,
                InvalidMarketFeaturesSnapshotException.class);
        errorHandler.setRetryListeners(deadLetterMetrics);
        return errorHandler;
    }

    @Bean
    public ConsumerFactory<String, MarketFeaturesSnapshotEvent> marketFeaturesConsumerFactory(
            KafkaProperties kafkaProperties,
            @Value("${app.kafka.schema-registry.url:http://localhost:8081}") String schemaRegistryUrl) {
        return new DefaultKafkaConsumerFactory<>(avroConsumerProps(kafkaProperties, schemaRegistryUrl));
    }

    @Bean
    @SuppressWarnings("unchecked")
    public ConcurrentKafkaListenerContainerFactory<String, MarketFeaturesSnapshotEvent>
            marketFeaturesKafkaListenerContainerFactory(
                    ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
                    ConsumerFactory<String, MarketFeaturesSnapshotEvent> marketFeaturesConsumerFactory,
                    CommonErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, MarketFeaturesSnapshotEvent>();
        // Applies spring.kafka.listener.* (concurrency, ack-mode, poll-timeout, auto-startup, ...).
        // Boot's configurer is typed <Object, Object>; the cast is safe, it only sets container props.
        configurer.configure(
                (ConcurrentKafkaListenerContainerFactory<Object, Object>) (ConcurrentKafkaListenerContainerFactory<?, ?>) factory,
                (ConsumerFactory<Object, Object>) (ConsumerFactory<?, ?>) marketFeaturesConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ProducerFactory<String, MarketInterpretationSnapshotEvent> marketInterpretationsProducerFactory(
            KafkaProperties kafkaProperties,
            @Value("${app.kafka.schema-registry.url:http://localhost:8081}") String schemaRegistryUrl) {
        return new DefaultKafkaProducerFactory<>(avroProducerProps(kafkaProperties, schemaRegistryUrl));
    }

    @Bean
    public KafkaTemplate<String, MarketInterpretationSnapshotEvent> marketInterpretationsKafkaTemplate(
            ProducerFactory<String, MarketInterpretationSnapshotEvent> marketInterpretationsProducerFactory) {
        return new KafkaTemplate<>(marketInterpretationsProducerFactory);
    }
}
