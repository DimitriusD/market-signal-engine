package com.trading.marketsignalengine.event.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;

/**
 * Counts listener-level failures: every retry attempt ({@code mse.consume.retries}) and every record
 * that exhausted retries or was non-retryable and got dead-lettered ({@code mse.dlt.records}), tagged
 * by exception class so "why did records go to the DLT" is answerable from metrics alone.
 */
public final class DeadLetterMetrics implements RetryListener {

    static final String RETRIES = "mse.consume.retries";
    static final String DLT_RECORDS = "mse.dlt.records";
    static final String TAG_EXCEPTION = "exception";
    static final String TAG_TOPIC = "topic";

    private final MeterRegistry registry;

    public DeadLetterMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
        Counter.builder(RETRIES)
                .description("Failed deliveries of an input record (each attempt)")
                .tag(TAG_TOPIC, record.topic())
                .tag(TAG_EXCEPTION, exceptionTag(ex))
                .register(registry)
                .increment();
    }

    @Override
    public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
        Counter.builder(DLT_RECORDS)
                .description("Input records dead-lettered after retries were exhausted or skipped")
                .tag(TAG_TOPIC, record.topic())
                .tag(TAG_EXCEPTION, exceptionTag(ex))
                .register(registry)
                .increment();
    }

    @Override
    public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
        Counter.builder("mse.dlt.failures")
                .description("Dead-letter publish itself failed (record may be redelivered)")
                .tag(TAG_TOPIC, record.topic())
                .tag(TAG_EXCEPTION, exceptionTag(failure))
                .register(registry)
                .increment();
    }

    /** Unwraps the listener-execution wrapper so the tag names the real cause. */
    static String exceptionTag(Throwable ex) {
        Throwable t = ex;
        while (t != null && t.getClass().getName().startsWith("org.springframework.kafka.listener.ListenerExecutionFailedException")
                && t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t == null ? "unknown" : t.getClass().getSimpleName();
    }
}
