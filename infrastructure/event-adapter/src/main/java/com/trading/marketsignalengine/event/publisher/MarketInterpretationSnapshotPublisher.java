package com.trading.marketsignalengine.event.publisher;

import com.trading.contracts.signal.MarketInterpretationSnapshotEvent;
import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationPublication;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationSnapshotPublisherPort;
import com.trading.marketsignalengine.event.mapper.MarketInterpretationSnapshotAvroMapper;
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
 * Publishes an interpretation snapshot and waits for the broker acknowledgement with a <b>bounded</b>
 * timeout, so the consumer thread never blocks indefinitely on a slow/unreachable broker.
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
 * downstream deduplicates on the deterministic {@code interpretationSnapshotId}.
 *
 * <p>The Kafka key is the domain-required {@code instrumentId} — non-blank by aggregate invariant,
 * so no fallback key exists. Fail-fast: a {@code null} publication, a blank topic or a non-positive
 * timeout are programming / configuration errors and throw immediately — nothing is logged-and-skipped.
 */
@Slf4j
public final class MarketInterpretationSnapshotPublisher implements MarketInterpretationSnapshotPublisherPort {

    private final KafkaTemplate<String, MarketInterpretationSnapshotEvent> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    public MarketInterpretationSnapshotPublisher(
            KafkaTemplate<String, MarketInterpretationSnapshotEvent> kafkaTemplate,
            String topic,
            Duration publishTimeout) {
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
    public void publish(MarketInterpretationPublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("MarketInterpretationPublication to publish must not be null");
        }
        MarketInterpretationSnapshot snapshot = publication.snapshot();

        MarketInterpretationSnapshotEvent event = MarketInterpretationSnapshotAvroMapper.toAvro(publication);
        String key = snapshot.instrumentId();

        CompletableFuture<SendResult<String, MarketInterpretationSnapshotEvent>> future =
                kafkaTemplate.send(topic, key, event);
        try {
            future.get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            // Stop waiting; the producer may still deliver (see class doc) — the retry path must
            // tolerate a duplicate output, which carries the same deterministic interpretationSnapshotId.
            future.cancel(true);
            throw failed(snapshot, key, new SignalPublishException(
                    "Publish of interpretationSnapshotId=" + snapshot.interpretationSnapshotId() + " to " + topic
                            + " timed out after " + publishTimeout.toMillis() + " ms (delivery outcome unknown)", ex));
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw failed(snapshot, key, new SignalPublishException(
                    "Publish of interpretationSnapshotId=" + snapshot.interpretationSnapshotId() + " to " + topic
                            + " failed", cause));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw failed(snapshot, key, new SignalPublishException(
                    "Publish of interpretationSnapshotId=" + snapshot.interpretationSnapshotId() + " to " + topic
                            + " interrupted", ex));
        }

        log.info(
                "Published market interpretation snapshot: topic={}, key={}, interpretationSnapshotId={}, "
                        + "sourceFeatureEventId={}, qualityStatus={}, eligibleForTrading={}, opportunityStatus={}, "
                        + "opportunityType={}, opportunitySide={}, validUntil={}",
                topic,
                key,
                snapshot.interpretationSnapshotId(),
                snapshot.featureLineage().sourceFeatureEventId(),
                snapshot.interpretationQuality().status(),
                snapshot.isEligibleForTrading(),
                snapshot.marketOpportunity().status(),
                snapshot.marketOpportunity().type(),
                snapshot.marketOpportunity().side(),
                snapshot.validUntil());
    }

    private SignalPublishException failed(MarketInterpretationSnapshot snapshot, String key,
                                          SignalPublishException ex) {
        log.error(
                "Failed to publish market interpretation snapshot: topic={}, key={}, interpretationSnapshotId={}, "
                        + "sourceFeatureEventId={}, qualityStatus={}, opportunityStatus={}",
                topic,
                key,
                snapshot.interpretationSnapshotId(),
                snapshot.featureLineage().sourceFeatureEventId(),
                snapshot.interpretationQuality().status(),
                snapshot.marketOpportunity().status(),
                ex);
        return ex;
    }
}
