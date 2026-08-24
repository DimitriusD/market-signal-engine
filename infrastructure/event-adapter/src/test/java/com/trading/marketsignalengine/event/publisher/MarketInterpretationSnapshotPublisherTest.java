package com.trading.marketsignalengine.event.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.contracts.signal.MarketInterpretationSnapshotEvent;
import com.trading.marketsignalengine.application.domain.interpretation.CrossHorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.FeatureLineage;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationLineage;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationQuality;
import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.MarketOpportunity;
import com.trading.marketsignalengine.application.domain.interpretation.MarketRegime;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunitySide;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityType;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationPublication;
import com.trading.marketsignalengine.event.mapper.MarketInterpretationSnapshotAvroMapper;
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
 * Bounded-publish behaviour of the V2 publisher without a broker: the template is stubbed so
 * {@code send()} returns a future we control. Every V1 delivery scenario is preserved — broker ack,
 * exact topic/key/event, blank topic, null publication, non-positive timeout, broker failure,
 * timeout with abandoned wait, interrupted wait — plus the deterministic id on retry.
 */
class MarketInterpretationSnapshotPublisherTest {

    private static final String TOPIC = "state.market.signals.v1";

    @Test
    void acknowledgedSendPublishesTheExactV2EventKeyedByInstrumentId() {
        String[] sentTopic = new String[1];
        String[] sentKey = new String[1];
        MarketInterpretationSnapshotEvent[] sent = new MarketInterpretationSnapshotEvent[1];
        KafkaTemplate<String, MarketInterpretationSnapshotEvent> template = new StubTemplate() {
            @Override
            public CompletableFuture<SendResult<String, MarketInterpretationSnapshotEvent>> send(
                    String topic, String key, MarketInterpretationSnapshotEvent data) {
                sentTopic[0] = topic;
                sentKey[0] = key;
                sent[0] = data;
                ProducerRecord<String, MarketInterpretationSnapshotEvent> record =
                        new ProducerRecord<>(topic, key, data);
                RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
                return CompletableFuture.completedFuture(new SendResult<>(record, metadata));
            }
        };
        MarketInterpretationSnapshotPublisher publisher =
                new MarketInterpretationSnapshotPublisher(template, TOPIC, Duration.ofSeconds(1));
        MarketInterpretationPublication publication = publication();

        publisher.publish(publication);

        assertEquals(TOPIC, sentTopic[0]);
        assertEquals("binance:spot:BTCUSDT", sentKey[0], "the key is the domain-required instrumentId");
        assertEquals(MarketInterpretationSnapshotAvroMapper.toAvro(publication), sent[0],
                "exactly the mapped V2 event is sent");
        assertEquals(publication.snapshot().interpretationSnapshotId(), sent[0].getMetadata().getEventId());
    }

    @Test
    void repeatedPublishOfTheSamePublicationCarriesTheSameDeterministicId() {
        MarketInterpretationPublication publication = publication();

        MarketInterpretationSnapshotEvent first = MarketInterpretationSnapshotAvroMapper.toAvro(publication);
        MarketInterpretationSnapshotEvent second = MarketInterpretationSnapshotAvroMapper.toAvro(publication);

        assertEquals(first.getMetadata().getEventId(), second.getMetadata().getEventId(),
                "a retry after an ambiguous timeout dedupes downstream on the interpretationSnapshotId");
    }

    @Test
    void neverCompletingSendFailsWithinTimeoutAndAbandonsTheWait() {
        CompletableFuture<SendResult<String, MarketInterpretationSnapshotEvent>> pending = new CompletableFuture<>();
        MarketInterpretationSnapshotPublisher publisher = new MarketInterpretationSnapshotPublisher(
                templateReturning(pending), TOPIC, Duration.ofMillis(200));

        long start = System.nanoTime();
        SignalPublishException ex = assertThrows(SignalPublishException.class, () -> publisher.publish(publication()));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertInstanceOf(TimeoutException.class, ex.getCause());
        assertTrue(ex.getMessage().contains("timed out after 200 ms"), ex.getMessage());
        assertTrue(ex.getMessage().contains("interpretationSnapshotId="), ex.getMessage());
        assertTrue(elapsedMs < 5_000, "publish must not block beyond the timeout, took " + elapsedMs + " ms");
        // cancel() only detaches the waiter — it does not guarantee the producer drops the record
        assertTrue(pending.isCancelled(), "the wait must be abandoned on timeout");
    }

    @Test
    void brokerFailureIsWrappedWithItsCause() {
        CompletableFuture<SendResult<String, MarketInterpretationSnapshotEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("Topic not present"));
        MarketInterpretationSnapshotPublisher publisher = new MarketInterpretationSnapshotPublisher(
                templateReturning(failed), TOPIC, Duration.ofSeconds(1));

        SignalPublishException ex = assertThrows(SignalPublishException.class, () -> publisher.publish(publication()));

        assertInstanceOf(org.apache.kafka.common.errors.TimeoutException.class, ex.getCause());
    }

    @Test
    void nonPositiveTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationSnapshotPublisher(
                new StubTemplate(), "t", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationSnapshotPublisher(
                new StubTemplate(), "t", Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationSnapshotPublisher(
                new StubTemplate(), "t", null));
    }

    @Test
    void blankTopicIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationSnapshotPublisher(
                new StubTemplate(), " ", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new MarketInterpretationSnapshotPublisher(
                new StubTemplate(), null, Duration.ofSeconds(1)));
    }

    @Test
    void nullPublicationIsAnExplicitFailureNotASilentSkip() {
        boolean[] sendCalled = new boolean[1];
        MarketInterpretationSnapshotPublisher publisher = new MarketInterpretationSnapshotPublisher(new StubTemplate() {
            @Override
            public CompletableFuture<SendResult<String, MarketInterpretationSnapshotEvent>> send(
                    String topic, String key, MarketInterpretationSnapshotEvent data) {
                sendCalled[0] = true;
                return new CompletableFuture<>();
            }
        }, TOPIC, Duration.ofSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(null));
        assertFalse(sendCalled[0], "nothing must be sent for a null publication");
    }

    @Test
    void interruptedWaitPreservesTheInterruptFlag() throws InterruptedException {
        CompletableFuture<SendResult<String, MarketInterpretationSnapshotEvent>> pending = new CompletableFuture<>();
        MarketInterpretationSnapshotPublisher publisher = new MarketInterpretationSnapshotPublisher(
                templateReturning(pending), TOPIC, Duration.ofSeconds(10));
        Throwable[] thrown = new Throwable[1];
        boolean[] interruptedAfter = new boolean[1];
        Thread worker = new Thread(() -> {
            try {
                publisher.publish(publication());
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

    // ------------------------------------------------------------------ helpers

    private static KafkaTemplate<String, MarketInterpretationSnapshotEvent> templateReturning(
            CompletableFuture<SendResult<String, MarketInterpretationSnapshotEvent>> future) {
        return new StubTemplate() {
            @Override
            public CompletableFuture<SendResult<String, MarketInterpretationSnapshotEvent>> send(
                    String topic, String key, MarketInterpretationSnapshotEvent data) {
                return future;
            }
        };
    }

    /** A KafkaTemplate that never touches a broker; subclasses override {@code send}. */
    private static class StubTemplate extends KafkaTemplate<String, MarketInterpretationSnapshotEvent> {
        StubTemplate() {
            super(new DefaultKafkaProducerFactory<>(Map.of("bootstrap.servers", "localhost:1")), false);
        }
    }

    private static MarketInterpretationPublication publication() {
        Instant evaluatedAt = Instant.parse("2026-03-01T10:00:00Z");
        EvidenceStrength strength = EvidenceStrength.of("0.6");
        InterpretationDirection direction = InterpretationDirection.BULLISH;
        List<MarketHorizon> all =
                List.of(MarketHorizon.H1S, MarketHorizon.H5S, MarketHorizon.H15S, MarketHorizon.H60S);
        MarketInterpretationSnapshot snapshot = MarketInterpretationSnapshot.builder()
                .exchange("binance").marketType("spot").base("BTC").quote("USDT")
                .symbol("BTCUSDT").instrumentId("binance:spot:BTCUSDT")
                .evaluatedAt(evaluatedAt).validUntil(evaluatedAt.plusMillis(1_750))
                .interpretationQuality(InterpretationQuality.ok(List.of()))
                .horizonAssessments(all.stream().map(h -> HorizonAssessment.eligible(h, direction, strength,
                        MarketRegime.TRENDING,
                        List.of(EvidenceAssessment.available(EvidenceDimension.FLOW, direction, strength, List.of())),
                        List.of())).toList())
                .crossHorizonAssessment(CrossHorizonAssessment.alignedBullish(strength, MarketHorizon.H60S, all,
                        MarketRegime.TRENDING, List.of(ReasonCode.of("CROSS_HORIZON_ALIGNED_BULLISH"))))
                .marketOpportunity(MarketOpportunity.candidate(OpportunityType.MOMENTUM_CONTINUATION,
                        OpportunitySide.LONG, MarketHorizon.H5S, strength,
                        List.of(ReasonCode.of("OPPORTUNITY_MOMENTUM_CONTINUATION_CANDIDATE")),
                        List.of(ReasonCode.of("OPPORTUNITY_INVALIDATE_QUALITY"))))
                .featureLineage(new FeatureLineage("feat-1", 1, "mfs-features-v2", "cfg-feat-1",
                        evaluatedAt, evaluatedAt.plusMillis(25), "TRADE"))
                .interpretationLineage(new InterpretationLineage("mse-interpretation-v1", "cfg-int-1"))
                .build();
        return new MarketInterpretationPublication(snapshot,
                evaluatedAt.plusMillis(100), evaluatedAt.plusMillis(105));
    }
}
