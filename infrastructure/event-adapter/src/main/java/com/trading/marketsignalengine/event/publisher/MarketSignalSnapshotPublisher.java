package com.trading.marketsignalengine.event.publisher;

import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.event.mapper.MarketSignalSnapshotAvroMapper;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Publishes a signal snapshot and waits for the broker acknowledgement with a <b>bounded</b> timeout,
 * so the consumer thread never blocks indefinitely on a slow/unreachable broker.
 *
 * <p><b>Delivery semantics: at-least-once.</b> The input offset is only committed after this method
 * returns normally, i.e. after the output was acknowledged. On timeout the publisher stops waiting and
 * throws {@link SignalPublishException}, which the listener error handler retries with back-off and
 * finally dead-letters. Abandoning the wait ({@code future.cancel}) only detaches this thread from
 * the result — it does <em>not</em> and cannot guarantee the Kafka producer will not still deliver
 * the record (the producer keeps trying until its own {@code delivery.timeout.ms}). A listener retry
 * after an ambiguous timeout can therefore produce a <em>duplicate</em> output event. That is why the
 * timeout hierarchy is {@code request.timeout.ms < delivery.timeout.ms < publish timeout} (the
 * producer gives up before the application does, so most timeouts are unambiguous) and why
 * downstream deduplicates on the deterministic {@code signalSnapshotId}.
 *
 * <p>Fail-fast: a {@code null} snapshot, a blank topic or a non-positive timeout are programming /
 * configuration errors and throw immediately — nothing is logged-and-skipped.
 */
@Slf4j
public final class MarketSignalSnapshotPublisher implements MarketSignalSnapshotPublisherPort {

    private final KafkaTemplate<String, MarketSignalSnapshotEvent> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    public MarketSignalSnapshotPublisher(
            KafkaTemplate<String, MarketSignalSnapshotEvent> kafkaTemplate, String topic, Duration publishTimeout) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("publish topic must not be blank");
        }
        if (publishTimeout == null || publishTimeout.isZero() || publishTimeout.isNegative()) {
            throw new IllegalArgumentException("publishTimeout must be positive, got " + publishTimeout);
        }
        this.topic = topic;
        this.publishTimeout = publishTimeout;
    }

    public Duration publishTimeout() {
        return publishTimeout;
    }

    public String topic() {
        return topic;
    }

    @Override
    public void publish(MarketSignalSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("MarketSignalSnapshot to publish must not be null");
        }

        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(snapshot);
        String key = resolveKey(snapshot);

        CompletableFuture<SendResult<String, MarketSignalSnapshotEvent>> future = kafkaTemplate.send(topic, key, event);
        try {
            future.get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            // Stop waiting; the producer may still deliver (see class doc) — the retry path must
            // tolerate a duplicate output, which carries the same deterministic signalSnapshotId.
            future.cancel(true);
            throw failed(snapshot, key, new SignalPublishException(
                    "Publish of signalSnapshotId=" + snapshot.signalSnapshotId() + " to " + topic
                            + " timed out after " + publishTimeout.toMillis() + " ms (delivery outcome unknown)", ex));
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw failed(snapshot, key, new SignalPublishException(
                    "Publish of signalSnapshotId=" + snapshot.signalSnapshotId() + " to " + topic + " failed", cause));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw failed(snapshot, key, new SignalPublishException(
                    "Publish of signalSnapshotId=" + snapshot.signalSnapshotId() + " to " + topic + " interrupted", ex));
        }

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
    }

    private SignalPublishException failed(MarketSignalSnapshot snapshot, String key, SignalPublishException ex) {
        log.error(
                "Failed to publish market signal snapshot: topic={}, key={}, signalSnapshotId={}, sourceFeatureSnapshotId={}",
                topic,
                key,
                snapshot.signalSnapshotId(),
                snapshot.sourceFeatureSnapshotId(),
                ex);
        return ex;
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
