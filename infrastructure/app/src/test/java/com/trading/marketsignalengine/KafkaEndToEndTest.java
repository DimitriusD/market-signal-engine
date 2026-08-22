package com.trading.marketsignalengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.contracts.common.MetadataEvent;
import com.trading.contracts.feature.BboFeaturesEvent;
import com.trading.contracts.feature.BookFeaturesEvent;
import com.trading.contracts.feature.FeatureQualityEvent;
import com.trading.contracts.feature.FeatureQualityStatus;
import com.trading.contracts.feature.FeatureSourceStateEvent;
import com.trading.contracts.feature.MarketFeaturesSnapshotEvent;
import com.trading.contracts.feature.ShortTermRegimeFeaturesEvent;
import com.trading.contracts.feature.TradeFlowFeaturesEvent;
import com.trading.contracts.orderbook.BookSyncStatus;
import com.trading.contracts.signal.MarketSignalSnapshotEvent;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * consume → evaluate → publish against an in-JVM Kafka (KRaft) and a {@code mock://} Schema Registry
 * shared by the application and the test producer/consumer. Covers: a live MFS v2 event becomes a
 * signal snapshot with full lineage; the same input twice yields the same deterministic
 * {@code signalSnapshotId}; an unsupported {@code featureSetVersion} is dead-lettered to
 * {@code <input>.DLT} and counted in metrics. No Docker required.
 */
@SpringBootTest(properties = {
        "app.kafka.schema-registry.url=mock://mse-e2e",
        "app.kafka.topic.market-features=" + KafkaEndToEndTest.INPUT_TOPIC,
        "app.kafka.topic.market-signals=" + KafkaEndToEndTest.OUTPUT_TOPIC,
        "spring.kafka.consumer.group-id=mse-e2e-" + "group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.retry.backoff-ms=50",
        "app.kafka.retry.max-attempts=1",
        "app.kafka.publish-timeout-ms=10000"
})
@EmbeddedKafka(
        kraft = true,
        partitions = 1,
        topics = {KafkaEndToEndTest.INPUT_TOPIC, KafkaEndToEndTest.OUTPUT_TOPIC, KafkaEndToEndTest.DLT_TOPIC},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class KafkaEndToEndTest {

    static final String INPUT_TOPIC = "e2e.market.feature.snapshot.v1";
    static final String OUTPUT_TOPIC = "e2e.state.market.signals.v1";
    static final String DLT_TOPIC = INPUT_TOPIC + ".DLT";
    private static final String SCHEMA_REGISTRY = "mock://mse-e2e";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    private EmbeddedKafkaBroker broker;
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
                avroConsumerProps(broker, "e2e-signals-reader")).createConsumer();
        broker.consumeFromAnEmbeddedTopic(signalsConsumer, OUTPUT_TOPIC);

        dltConsumer = new DefaultKafkaConsumerFactory<String, Object>(
                avroConsumerProps(broker, "e2e-dlt-reader")).createConsumer();
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
    void liveMfsV2SnapshotBecomesSignalSnapshotWithLineage() throws Exception {
        String featureEventId = "feat-" + UUID.randomUUID();
        MarketFeaturesSnapshotEvent input = tradableEvent(featureEventId, "mfs-features-v2");

        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, MarketSignalSnapshotEvent> out = awaitSignal(featureEventId);
        MarketSignalSnapshotEvent signal = out.value();
        assertEquals("binance:spot:BTCUSDT", out.key());
        assertEquals(featureEventId, signal.getSourceFeatureEventId());
        assertEquals("mfs-features-v2", signal.getSourceFeatureSetVersion());
        assertEquals("mse-signals-v8", signal.getSignalSetVersion());
        assertEquals("BULLISH", signal.getMarketBias());
        assertEquals("NORMAL", signal.getRiskLevel());
        assertEquals("LONG", signal.getSetup().getSide());
        assertTrue(signal.getValidUntilTs() > signal.getEvaluatedTs());
        assertTrue(signal.getSignals().stream().anyMatch(s -> "LONG_SETUP_FORMING".equals(s.getType())));
        assertNotNull(signal.getMetadata().getEventId());

        // Metrics answer "what happened" without logs.
        assertTrue(meterRegistry.find("mse.snapshots").tag("riskLevel", "NORMAL").counter().count() >= 1.0);
        assertTrue(meterRegistry.find("mse.publish.duration").tag("outcome", "ok").timer().count() >= 1);
    }

    @Test
    void duplicateInputYieldsTheSameDeterministicSignalSnapshotId() throws Exception {
        String featureEventId = "feat-dup-" + UUID.randomUUID();
        MarketFeaturesSnapshotEvent input = tradableEvent(featureEventId, "mfs-features-v2");

        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);
        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);

        List<MarketSignalSnapshotEvent> outputs = awaitSignals(featureEventId, 2);
        assertEquals(2, outputs.size());
        assertEquals(outputs.get(0).getMetadata().getEventId(), outputs.get(1).getMetadata().getEventId(),
                "re-processing the same feature snapshot must produce the same signalSnapshotId");
    }

    @Test
    void unsupportedFeatureSetVersionIsDeadLetteredNotPublished() throws Exception {
        String featureEventId = "feat-bad-" + UUID.randomUUID();
        MarketFeaturesSnapshotEvent input = tradableEvent(featureEventId, "mfs-features-v99");

        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, Object> dead = awaitDlt(featureEventId);
        assertNotNull(dead);
        assertEquals("binance:spot:BTCUSDT", dead.key());
        assertFalse(signalPublishedFor(featureEventId, Duration.ofSeconds(2)),
                "a rejected input must not produce a signal snapshot");
        assertTrue(meterRegistry.find("mse.dlt.records")
                .tag("exception", "InvalidMarketFeaturesSnapshotException").counter().count() >= 1.0);
    }

    // ------------------------------------------------------------------ helpers

    private ConsumerRecord<String, MarketSignalSnapshotEvent> awaitSignal(String featureEventId) {
        return awaitSignalRecords(featureEventId, 1).getFirst();
    }

    private List<MarketSignalSnapshotEvent> awaitSignals(String featureEventId, int count) {
        return awaitSignalRecords(featureEventId, count).stream().map(ConsumerRecord::value).toList();
    }

    private List<ConsumerRecord<String, MarketSignalSnapshotEvent>> awaitSignalRecords(String featureEventId, int count) {
        List<ConsumerRecord<String, MarketSignalSnapshotEvent>> found = new ArrayList<>();
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline && found.size() < count) {
            ConsumerRecords<String, MarketSignalSnapshotEvent> records = signalsConsumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, MarketSignalSnapshotEvent> r : records) {
                if (featureEventId.equals(r.value().getSourceFeatureEventId())) {
                    found.add(r);
                }
            }
        }
        assertEquals(count, found.size(), "expected " + count + " signal(s) for " + featureEventId + " within " + WAIT);
        return found;
    }

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

    /** A tradable MFS v2 snapshot whose 5s flow + top5 book form a LONG microstructure setup. */
    private static MarketFeaturesSnapshotEvent tradableEvent(String eventId, String featureSetVersion) {
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
                .setEvaluationTs(exchangeTs + 35)
                .setFeatureSetVersion(featureSetVersion)
                .setTriggerSource("MARKET_EVENT")
                .setConfigHash("cfg-e2e")
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
                        // The published trading-schemas jar embeds the 15s/60s counters as non-null
                        // int (default 0) inside MarketFeaturesSnapshotEvent's schema even though the
                        // component .avsc declares ["null","int"]; a producer must therefore set them
                        // (MFS sends 0 while warming up). Kept explicit here so the test mirrors the wire.
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
