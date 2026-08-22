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
 * Publishes a signal snapshot and waits for the broker acknowledgement with a <b>bounded</b> timeout.
 * The consumer thread must never block indefinitely on a slow/unreachable broker: on timeout the
 * pending send is cancelled and {@link SignalPublishException} is thrown, which the listener error
 * handler treats like any other failure (retry with back-off, then DLT). Keeping publish synchronous
 * preserves at-least-once semantics — the input offset is only committed after the output is acked.
 */
@Slf4j
public final class MarketSignalSnapshotPublisher implements MarketSignalSnapshotPublisherPort {

    private final KafkaTemplate<String, MarketSignalSnapshotEvent> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    public MarketSignalSnapshotPublisher(
            KafkaTemplate<String, MarketSignalSnapshotEvent> kafkaTemplate, String topic, Duration publishTimeout) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.topic = Objects.requireNonNull(topic, "topic");
        if (publishTimeout == null || publishTimeout.isZero() || publishTimeout.isNegative()) {
            throw new IllegalArgumentException("publishTimeout must be positive");
        }
        this.publishTimeout = publishTimeout;
    }

    public Duration publishTimeout() {
        return publishTimeout;
    }

    @Override
    public void publish(MarketSignalSnapshot snapshot) {
        if (snapshot == null) {
            log.warn("Skipping null market signal snapshot publish");
            return;
        }

        MarketSignalSnapshotEvent event = MarketSignalSnapshotAvroMapper.toAvro(snapshot);
        String key = resolveKey(snapshot);

        CompletableFuture<SendResult<String, MarketSignalSnapshotEvent>> future = kafkaTemplate.send(topic, key, event);
        try {
            future.get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw failed(snapshot, key, new SignalPublishException(
                    "Publish of signalSnapshotId=" + snapshot.signalSnapshotId() + " to " + topic
                            + " timed out after " + publishTimeout.toMillis() + " ms", ex));
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
