package com.trading.marketsignalengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.contracts.common.MetadataEvent;
import com.trading.contracts.feature.BboFeaturesEvent;
import com.trading.contracts.feature.BookFeaturesEvent;
import com.trading.contracts.feature.DiagnosticsEvent;
import com.trading.contracts.feature.FeatureQualityEvent;
import com.trading.contracts.feature.FeatureQualityStatus;
import com.trading.contracts.feature.FeatureSourceStateEvent;
import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.contracts.feature.ShortTermRegimeFeaturesEvent;
import com.trading.contracts.feature.TradeFlowFeaturesEvent;
import com.trading.contracts.orderbook.BookSyncStatus;
import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.event.publisher.SignalPublishException;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Publish failure → bounded listener retry → DLT, on an in-JVM Kafka (KRaft) + {@code mock://}
 * Schema Registry. The real consumer, mapper, validated evaluator and error handler run; only the
 * output publisher port is replaced by a controlled test double that always throws
 * {@link SignalPublishException} (the retryable failure class) and counts attempts.
 *
 * <p>Retry semantics under test: {@code app.kafka.retry.max-attempts} configures
 * {@code FixedBackOff.maxAttempts}, which is the number of <em>retries after the first delivery</em>.
 * With {@code max-attempts=2} the record is delivered 3 times (3 publisher attempts) and then the
 * original {@code MarketFeaturesSnapshotEvent} lands on {@code <input>.DLT}; no V1 output is
 * published.
 */
@SpringBootTest(properties = {
        "app.kafka.schema-registry.url=mock://mse-dlt",
        "app.kafka.topic.market-features=" + KafkaPublishFailureDltTest.INPUT_TOPIC,
        "app.kafka.topic.market-signals=" + KafkaPublishFailureDltTest.OUTPUT_TOPIC,
        "spring.kafka.consumer.group-id=mse-dlt-group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.retry.backoff-ms=50",
        "app.kafka.retry.max-attempts=" + KafkaPublishFailureDltTest.MAX_ATTEMPTS,
        "app.kafka.publish-timeout-ms=6500"
})
@EmbeddedKafka(
        kraft = true,
        partitions = 1,
        topics = {KafkaPublishFailureDltTest.INPUT_TOPIC, KafkaPublishFailureDltTest.OUTPUT_TOPIC,
                KafkaPublishFailureDltTest.DLT_TOPIC},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(KafkaPublishFailureDltTest.FailingPublisherConfiguration.class)
@DirtiesContext
class KafkaPublishFailureDltTest {

    static final String INPUT_TOPIC = "dlt.market.feature.snapshot.v1";
    static final String OUTPUT_TOPIC = "dlt.state.market.signals.v1";
    static final String DLT_TOPIC = INPUT_TOPIC + ".DLT";
    /** FixedBackOff retries after the first delivery → EXPECTED_PUBLISH_ATTEMPTS total deliveries. */
    static final int MAX_ATTEMPTS = 2;
    static final int EXPECTED_PUBLISH_ATTEMPTS = MAX_ATTEMPTS + 1;
    private static final String SCHEMA_REGISTRY = "mock://mse-dlt";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @TestConfiguration
    static class FailingPublisherConfiguration {
        @Bean
        @Primary
        FailingPublisher failingPublisher() {
            return new FailingPublisher();
        }
    }

    /** Controlled output port: every publish throws the retryable failure and is counted. */
    static final class FailingPublisher implements MarketSignalSnapshotPublisherPort {
        final AtomicInteger attempts = new AtomicInteger();
        final List<String> attemptedSignalIds = new CopyOnWriteArrayList<>();

        @Override
        public void publish(MarketSignalSnapshot snapshot) {
            attempts.incrementAndGet();
            attemptedSignalIds.add(snapshot.signalSnapshotId());
            throw new SignalPublishException("simulated bounded publish failure for " + snapshot.signalSnapshotId(),
                    new TimeoutException("simulated"));
        }
    }

    @Autowired
    private FailingPublisher failingPublisher;
    @Autowired
    private MeterRegistry meterRegistry;

    private static KafkaTemplate<String, Object> producer;
    private static Consumer<String, MarketSignalSnapshotEvent> signalsConsumer;
    private static Consumer<String, Object> dltConsumer;

    @BeforeAll
    static void startClients(@Autowired EmbeddedKafkaBroker broker) {
        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(broker));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        producerProps.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY);
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        signalsConsumer = new DefaultKafkaConsumerFactory<String, MarketSignalSnapshotEvent>(
                avroConsumerProps(broker, "dlt-signals-reader")).createConsumer();
        broker.consumeFromAnEmbeddedTopic(signalsConsumer, OUTPUT_TOPIC);

        dltConsumer = new DefaultKafkaConsumerFactory<String, Object>(
                avroConsumerProps(broker, "dlt-dlt-reader")).createConsumer();
        broker.consumeFromAnEmbeddedTopic(dltConsumer, DLT_TOPIC);
    }

    @AfterAll
    static void stopClients() {
        if (signalsConsumer != null) {
            signalsConsumer.close();
        }
        if (dltConsumer != null) {
            dltConsumer.close();
        }
        if (producer != null) {
            producer.destroy();
        }
    }

    @Test
    void publishFailureIsRetriedExactlyPerPolicyThenInputIsDeadLetteredAndNoOutputIsPublished() throws Exception {
        String featureEventId = "feat-publish-fail-" + UUID.randomUUID();
        MarketFeaturesSnapshotEvent input = validTradeEvent(featureEventId);

        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);

        // 1. the original input record lands on <input>.DLT after the retries are exhausted
        ConsumerRecord<String, Object> dead = awaitDlt(featureEventId);
        assertNotNull(dead);
        assertEquals(input.getMetadata().getInstrumentId(), dead.key());
        MarketFeaturesSnapshotEvent deadValue = (MarketFeaturesSnapshotEvent) dead.value();
        assertEquals(featureEventId, deadValue.getMetadata().getEventId());
        assertEquals("mfs-features-v2", deadValue.getFeatureSetVersion());
        assertEquals("cfg-dlt", deadValue.getConfigHash());

        // 2. exactly first delivery + MAX_ATTEMPTS retries reached the publisher — no more, no less
        //    (settle a little to catch any spurious extra delivery)
        Thread.sleep(500);
        assertEquals(EXPECTED_PUBLISH_ATTEMPTS, failingPublisher.attempts.get(),
                "FixedBackOff(maxAttempts=" + MAX_ATTEMPTS + ") must yield " + EXPECTED_PUBLISH_ATTEMPTS + " deliveries");
        // every retry re-evaluated the same input deterministically → same signalSnapshotId
        assertEquals(1, failingPublisher.attemptedSignalIds.stream().distinct().count());

        // 3. no V1 output event was published for this input
        assertFalse(signalPublishedFor(featureEventId, Duration.ofSeconds(2)),
                "a never-acknowledged publish must not leave an output event");

        // 4. existing metrics (unchanged) attribute the dead-letter to the retryable exception
        assertTrue(meterRegistry.find("mse.dlt.records")
                .tag("exception", "SignalPublishException").counter().count() >= 1.0);
        assertTrue(meterRegistry.find("mse.consume.retries").counter() == null
                        || meterRegistry.find("mse.consume.retries").counter().count() >= MAX_ATTEMPTS,
                "retries must be observable");
    }

    // ------------------------------------------------------------------ helpers

    private boolean signalPublishedFor(String featureEventId, Duration window) {
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, MarketSignalSnapshotEvent> r : signalsConsumer.poll(Duration.ofMillis(200))) {
                if (featureEventId.equals(r.value().getSourceFeatureEventId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private ConsumerRecord<String, Object> awaitDlt(String featureEventId) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, Object> r : dltConsumer.poll(Duration.ofMillis(500))) {
                if (r.value() instanceof MarketFeaturesSnapshotEvent e
                        && featureEventId.equals(e.getMetadata().getEventId())) {
                    return r;
                }
            }
        }
        throw new AssertionError("no DLT record for " + featureEventId + " within " + WAIT);
    }

    private static Map<String, Object> avroConsumerProps(EmbeddedKafkaBroker broker, String group) {
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.consumerProps(group, "true", broker));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return props;
    }

    /** A complete, contract-valid, tradable MFS v2 TRADE-triggered snapshot. */
    private static MarketFeaturesSnapshotEvent validTradeEvent(String eventId) {
        long exchangeTs = System.currentTimeMillis() - 150;
        return MarketFeaturesSnapshotEvent.newBuilder()
                .setMetadata(MetadataEvent.newBuilder()
                        .setSchemaVersion(1)
                        .setEventType("MARKET_FEATURES_SNAPSHOT")
                        .setExchange("binance")
                        .setMarketType("spot")
                        .setBase("BTC")
                        .setQuote("USDT")
                        .setSymbol("BTCUSDT")
                        .setInstrumentId("binance:spot:BTCUSDT")
                        .setEventId(eventId)
                        .setSourceStream("market.feature.snapshot.v1")
                        .setExchangeTs(exchangeTs)
                        .setReceivedTs(exchangeTs + 20)
                        .setProcessedTs(exchangeTs + 30)
                        .build())
                .setComputedTs(exchangeTs + 35)
                .setEvaluationTs(exchangeTs)
                .setFeatureSetVersion("mfs-features-v2")
                .setTriggerSource("TRADE")
                .setConfigHash("cfg-dlt")
                .setQuality(FeatureQualityEvent.newBuilder()
                        .setSyncStatus(BookSyncStatus.IN_SYNC)
                        .setStaleOrderBookState(false)
                        .setStaleTrades(false)
                        .setIncompleteBook(false)
                        .setSourceOrderBookTrusted(true)
                        .setOrderBookStateAgeMs(40L)
                        .setTradeAgeMs(90L)
                        .setStatus(FeatureQualityStatus.OK)
                        .build())
                .setDiagnostics(DiagnosticsEvent.newBuilder().setTotalFeatureGroups(4).build())
                .setSourceState(FeatureSourceStateEvent.newBuilder().setPublishedDepth(20).build())
                .setBbo(BboFeaturesEvent.newBuilder()
                        .setBestBidPrice("50000.0").setBestAskPrice("50005.0")
                        .setBestBidQty("1.5").setBestAskQty("2.0")
                        .setSpreadAbs("5.0").setSpreadBps("1.0").setMidPrice("50002.5")
                        .build())
                .setBook(BookFeaturesEvent.newBuilder()
                        .setLevelsUsed(5).setTop1Imbalance("0.10").setTop5Imbalance("0.75")
                        .setBidLiquidityTop5("12.5").setAskLiquidityTop5("10.0")
                        .build())
                .setTradeFlow(TradeFlowFeaturesEvent.newBuilder()
                        .setLastTradePrice("50003.0")
                        .setTradeCount1s(9).setSignedFlowImbalance1s("0.33")
                        .setTradeCount5s(50).setSignedFlowImbalance5s("0.70")
                        .setBuyAggressiveVolume5s("4.0").setSellAggressiveVolume5s("2.0")
                        .setTradeCount15s(150).setValidQtyTradeCount15s(150)
                        .setAggressiveTradeCount15s(150).setUnknownSideCount15s(0)
                        .setTradeCount60s(600).setValidQtyTradeCount60s(600)
                        .setAggressiveTradeCount60s(600).setUnknownSideCount60s(0)
                        .build())
                .setRegime(ShortTermRegimeFeaturesEvent.newBuilder()
                        .setLastTradeDistanceToMidBps("0.4")
                        .setRealizedVolatilityBps1s("5.0")
                        .build())
                .build();
    }
}
