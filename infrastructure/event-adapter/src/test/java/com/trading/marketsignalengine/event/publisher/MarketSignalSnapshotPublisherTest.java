package com.trading.marketsignalengine.event.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.MarketBias;
import com.trading.marketsignalengine.application.domain.model.MarketSetup;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Bounded-publish behaviour without a broker: the template is stubbed so {@code send()} returns a
 * future we control. No Mockito on purpose — the overridden method is the only seam that matters.
 */
class MarketSignalSnapshotPublisherTest {

    @Test
    void neverCompletingSendFailsWithinTimeoutAndAbandonsTheWait() {
        CompletableFuture<SendResult<String, MarketSignalSnapshotEvent>> pending = new CompletableFuture<>();
        MarketSignalSnapshotPublisher publisher = new MarketSignalSnapshotPublisher(
                templateReturning(pending), "state.market.signals.v1", Duration.ofMillis(200));

        long start = System.nanoTime();
        SignalPublishException ex = assertThrows(SignalPublishException.class, () -> publisher.publish(snapshot()));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertInstanceOf(TimeoutException.class, ex.getCause());
        assertTrue(ex.getMessage().contains("timed out after 200 ms"), ex.getMessage());
        assertTrue(elapsedMs < 5_000, "publish must not block beyond the timeout, took " + elapsedMs + " ms");
        // cancel() only detaches the waiter — it does not guarantee the producer drops the record
        assertTrue(pending.isCancelled(), "the wait must be abandoned on timeout");
    }

    @Test
    void brokerFailureIsWrappedWithItsCause() {
        CompletableFuture<SendResult<String, MarketSignalSnapshotEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("Topic not present"));
        MarketSignalSnapshotPublisher publisher = new MarketSignalSnapshotPublisher(
                templateReturning(failed), "state.market.signals.v1", Duration.ofSeconds(1));

        SignalPublishException ex = assertThrows(SignalPublishException.class, () -> publisher.publish(snapshot()));

        assertInstanceOf(org.apache.kafka.common.errors.TimeoutException.class, ex.getCause());
    }

    @Test
    void acknowledgedSendCompletesNormally() {
        MarketSignalSnapshotEvent[] sent = new MarketSignalSnapshotEvent[1];
        KafkaTemplate<String, MarketSignalSnapshotEvent> template = new StubTemplate() {
            @Override
            public CompletableFuture<SendResult<String, MarketSignalSnapshotEvent>> send(
                    String topic, String key, MarketSignalSnapshotEvent data) {
                sent[0] = data;
                ProducerRecord<String, MarketSignalSnapshotEvent> record = new ProducerRecord<>(topic, key, data);
                RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
                return CompletableFuture.completedFuture(new SendResult<>(record, metadata));
            }
        };
        MarketSignalSnapshotPublisher publisher =
                new MarketSignalSnapshotPublisher(template, "state.market.signals.v1", Duration.ofSeconds(1));

        publisher.publish(snapshot());

        assertEquals("sig-1", sent[0].getMetadata().getEventId());
    }

    @Test
    void nonPositiveTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MarketSignalSnapshotPublisher(
                new StubTemplate(), "t", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new MarketSignalSnapshotPublisher(
                new StubTemplate(), "t", Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () -> new MarketSignalSnapshotPublisher(
                new StubTemplate(), "t", null));
    }

    @Test
    void blankTopicIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new MarketSignalSnapshotPublisher(
                new StubTemplate(), " ", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new MarketSignalSnapshotPublisher(
                new StubTemplate(), null, Duration.ofSeconds(1)));
    }

    @Test
    void nullSnapshotIsAnExplicitFailureNotASilentSkip() {
        boolean[] sendCalled = new boolean[1];
        MarketSignalSnapshotPublisher publisher = new MarketSignalSnapshotPublisher(new StubTemplate() {
            @Override
            public CompletableFuture<SendResult<String, MarketSignalSnapshotEvent>> send(
                    String topic, String key, MarketSignalSnapshotEvent data) {
                sendCalled[0] = true;
                return new CompletableFuture<>();
            }
        }, "state.market.signals.v1", Duration.ofSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(null));
        assertTrue(!sendCalled[0], "nothing must be sent for a null snapshot");
    }

    @Test
    void interruptedWaitPreservesTheInterruptFlag() throws InterruptedException {
        CompletableFuture<SendResult<String, MarketSignalSnapshotEvent>> pending = new CompletableFuture<>();
        MarketSignalSnapshotPublisher publisher = new MarketSignalSnapshotPublisher(
                templateReturning(pending), "state.market.signals.v1", Duration.ofSeconds(10));
        Throwable[] thrown = new Throwable[1];
        boolean[] interruptedAfter = new boolean[1];
        Thread worker = new Thread(() -> {
            try {
                publisher.publish(snapshot());
            } catch (Throwable t) {
                thrown[0] = t;
            }
            interruptedAfter[0] = Thread.currentThread().isInterrupted();
        });
        worker.start();
        Thread.sleep(200);
        worker.interrupt();
        worker.join(5_000);

        assertInstanceOf(SignalPublishException.class, thrown[0]);
        assertInstanceOf(InterruptedException.class, thrown[0].getCause());
        assertTrue(interruptedAfter[0], "interrupt flag must be restored for the caller");
    }

    private static KafkaTemplate<String, MarketSignalSnapshotEvent> templateReturning(
            CompletableFuture<SendResult<String, MarketSignalSnapshotEvent>> future) {
        return new StubTemplate() {
            @Override
            public CompletableFuture<SendResult<String, MarketSignalSnapshotEvent>> send(
                    String topic, String key, MarketSignalSnapshotEvent data) {
                return future;
            }
        };
    }

    /** A KafkaTemplate that never touches a broker; subclasses override {@code send}. */
    private static class StubTemplate extends KafkaTemplate<String, MarketSignalSnapshotEvent> {
        StubTemplate() {
            super(new DefaultKafkaProducerFactory<>(Map.of("bootstrap.servers", "localhost:1")), false);
        }
    }

    private static MarketSignalSnapshot snapshot() {
        Instant evaluatedAt = Instant.parse("2026-03-01T10:00:00.100Z");
        return new MarketSignalSnapshot(
                "sig-1",
                "feat-1",
                "binance",
                "spot",
                "BTC",
                "USDT",
                "BTCUSDT",
                "binance:spot:BTCUSDT",
                Instant.parse("2026-03-01T10:00:00Z"),
                evaluatedAt,
                evaluatedAt.plusMillis(1000),
                1000L,
                "mfs-features-v2",
                "mse-signals-v8",
                MarketBias.NEUTRAL,
                new BigDecimal("0.00"),
                RiskLevel.NORMAL,
                MarketSetup.none("no setup"),
                List.of(MarketSignal.neutral(SignalType.DATA_TRADABLE, SignalStrength.NONE, BigDecimal.ONE, "ok", Map.of())));
    }
}
