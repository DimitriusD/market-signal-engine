package com.trading.marketsignalengine.event.config;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;

/**
 * Startup-validated timeout hierarchy of the output path:
 * <pre>
 *   request.timeout.ms  &lt;  delivery.timeout.ms  &lt;  app.kafka.publish-timeout-ms
 * </pre>
 * Why: the publisher waits (bounded) on the consumer thread for the broker ack. If the application
 * stopped waiting <em>before</em> the producer gave up, the producer could still deliver the record
 * after the listener had already started retrying the input — a silent duplicate with no signal in
 * the logs. With the producer's own {@code delivery.timeout.ms} strictly inside the application wait,
 * a producer failure surfaces as an {@code ExecutionException} before the application timeout fires,
 * and an application timeout then only happens when the producer itself is wedged. Duplicates are
 * still possible in principle (at-least-once); downstream dedups on {@code signalSnapshotId}.
 *
 * <p>All three values must be positive; violations fail application startup with a clear message.
 */
public record PublishTimeoutHierarchy(long requestTimeoutMs, long deliveryTimeoutMs, long publishTimeoutMs) {

    /** Kafka producer defaults, used when the property is not configured explicitly. */
    public static final long KAFKA_DEFAULT_REQUEST_TIMEOUT_MS = 30_000L;
    public static final long KAFKA_DEFAULT_DELIVERY_TIMEOUT_MS = 120_000L;

    /**
     * Reads {@code request.timeout.ms} / {@code delivery.timeout.ms} from the effective producer
     * properties (Spring's {@code KafkaProperties#buildProducerProperties}), falling back to the Kafka
     * defaults when absent, and pairs them with the application publish timeout.
     */
    public static PublishTimeoutHierarchy from(Map<String, ?> producerProperties, long publishTimeoutMs) {
        return new PublishTimeoutHierarchy(
                longProperty(producerProperties, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, KAFKA_DEFAULT_REQUEST_TIMEOUT_MS),
                longProperty(producerProperties, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, KAFKA_DEFAULT_DELIVERY_TIMEOUT_MS),
                publishTimeoutMs);
    }

    /** @return this, for chaining; @throws IllegalStateException on any violation */
    public PublishTimeoutHierarchy validate() {
        requirePositive(requestTimeoutMs, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG);
        requirePositive(deliveryTimeoutMs, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);
        requirePositive(publishTimeoutMs, "app.kafka.publish-timeout-ms");
        if (requestTimeoutMs >= deliveryTimeoutMs) {
            throw new IllegalStateException("Invalid Kafka publish timeout hierarchy: "
                    + ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG + "=" + requestTimeoutMs + " must be < "
                    + ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG + "=" + deliveryTimeoutMs + describe());
        }
        if (deliveryTimeoutMs >= publishTimeoutMs) {
            throw new IllegalStateException("Invalid Kafka publish timeout hierarchy: "
                    + ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG + "=" + deliveryTimeoutMs
                    + " must be < app.kafka.publish-timeout-ms=" + publishTimeoutMs
                    + " (the producer must give up before the application stops waiting)" + describe());
        }
        return this;
    }

    private String describe() {
        return " [request=" + requestTimeoutMs + "ms, delivery=" + deliveryTimeoutMs + "ms, publish=" + publishTimeoutMs + "ms]";
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalStateException("Invalid Kafka publish timeout hierarchy: " + name + " must be positive, got " + value);
        }
    }

    private static long longProperty(Map<String, ?> properties, String key, long fallback) {
        Object value = properties == null ? null : properties.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value.toString().trim();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid Kafka producer property " + key + "='" + text + "': not a number", ex);
        }
    }
}
