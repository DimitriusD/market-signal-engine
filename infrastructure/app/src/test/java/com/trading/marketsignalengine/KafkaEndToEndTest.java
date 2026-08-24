package com.trading.marketsignalengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.trading.contracts.signal.HorizonAssessmentEvent;
import com.trading.contracts.signal.MarketInterpretationSnapshotEvent;
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
 * consume → multi-horizon interpretation → publish against an in-JVM Kafka (KRaft) and a
 * {@code mock://} Schema Registry shared by the application and the test producer/consumer. Covers:
 * a live MFS v2 event becomes a V2 {@code MarketInterpretationSnapshotEvent} on the unchanged
 * output topic with full nested assessments, metadata, lineage and opportunity; the same input twice
 * yields the same deterministic {@code interpretationSnapshotId}; an unsupported
 * {@code featureSetVersion} is dead-lettered to {@code <input>.DLT}. No Docker required.
 */
@SpringBootTest(properties = {
        "app.kafka.schema-registry.url=mock://mse-e2e",
        "app.kafka.topic.market-features=" + KafkaEndToEndTest.INPUT_TOPIC,
        "app.kafka.topic.market-signals=" + KafkaEndToEndTest.OUTPUT_TOPIC,
        "spring.kafka.consumer.group-id=mse-e2e-" + "group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.retry.backoff-ms=50",
        "app.kafka.retry.max-attempts=1",
        "app.kafka.publish-timeout-ms=10000",
        "app.interpretation.config-hash=cfg-e2e-interpretation-1"
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
    private MeterRegistry meterRegistry;

    private static KafkaTemplate<String, Object> producer;
    private static Consumer<String, MarketInterpretationSnapshotEvent> interpretationsConsumer;
    private static Consumer<String, Object> dltConsumer;

    @BeforeAll
    static void startClients(@Autowired EmbeddedKafkaBroker broker) {
        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(broker));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        producerProps.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY);
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        interpretationsConsumer = new DefaultKafkaConsumerFactory<String, MarketInterpretationSnapshotEvent>(
                avroConsumerProps(broker, "e2e-interpretations-reader")).createConsumer();
        broker.consumeFromAnEmbeddedTopic(interpretationsConsumer, OUTPUT_TOPIC);

        dltConsumer = new DefaultKafkaConsumerFactory<String, Object>(
                avroConsumerProps(broker, "e2e-dlt-reader")).createConsumer();
        broker.consumeFromAnEmbeddedTopic(dltConsumer, DLT_TOPIC);
    }

    @AfterAll
    static void stopClients() {
        if (interpretationsConsumer != null) {
            interpretationsConsumer.close();
        }
        if (dltConsumer != null) {
            dltConsumer.close();
        }
        if (producer != null) {
            producer.destroy();
        }
    }

    @Test
    void liveMfsV2SnapshotBecomesInterpretationSnapshotWithFullNestedContent() throws Exception {
        String featureEventId = "feat-" + UUID.randomUUID();
        long evaluationTs = System.currentTimeMillis() - 150;
        MarketFeaturesSnapshotEvent input = bullishContinuationEvent(featureEventId, "mfs-features-v2", evaluationTs);

        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, MarketInterpretationSnapshotEvent> out = awaitInterpretation(featureEventId);
        MarketInterpretationSnapshotEvent event = out.value();
        assertEquals("binance:spot:BTCUSDT", out.key(), "keyed by instrumentId on the unchanged topic");

        // metadata
        assertEquals(2, event.getMetadata().getSchemaVersion());
        assertEquals("MARKET_INTERPRETATION_SNAPSHOT", event.getMetadata().getEventType());
        assertEquals("market-signal-engine", event.getMetadata().getSourceStream());
        assertNotNull(event.getMetadata().getEventId());
        assertEquals(evaluationTs, event.getMetadata().getExchangeTs());
        assertEquals(evaluationTs, event.getEvaluatedTs(), "evaluatedTs is the source market tick");
        assertTrue(event.getMetadata().getReceivedTs() >= evaluationTs);
        assertTrue(event.getMetadata().getProcessedTs() >= event.getMetadata().getReceivedTs());

        // quality + validity: OK candidate on H5S → base 2000 − buffer 250
        assertEquals("OK", event.getQuality().getStatus());
        assertTrue(event.getQuality().getEligibleForTrading());
        assertEquals(evaluationTs + 1_750, event.getValidUntilTs());

        // horizons in canonical wire order, all eligible and bullish
        assertEquals(List.of("1S", "5S", "15S", "60S"),
                event.getHorizonAssessments().stream().map(HorizonAssessmentEvent::getHorizon).toList());
        for (HorizonAssessmentEvent horizon : event.getHorizonAssessments()) {
            assertEquals("ELIGIBLE", horizon.getEligibility().getStatus(), horizon.getHorizon());
            assertEquals("BULLISH", horizon.getDirection(), horizon.getHorizon());
            assertFalse(horizon.getEvidenceAssessments().isEmpty(), horizon.getHorizon());
        }

        // cross-horizon and opportunity: an interpreted setup, not a trade command
        assertEquals("ALIGNED_BULLISH", event.getCrossHorizonAssessment().getAlignment());
        assertEquals("60S", event.getCrossHorizonAssessment().getDominantHorizon());
        assertEquals(List.of("1S", "5S", "15S", "60S"), event.getCrossHorizonAssessment().getParticipatingHorizons());
        assertEquals("TRENDING", event.getCrossHorizonAssessment().getRegime());
        assertEquals("CANDIDATE", event.getOpportunity().getStatus());
        assertEquals("MOMENTUM_CONTINUATION", event.getOpportunity().getType());
        assertEquals("LONG", event.getOpportunity().getSide());
        assertEquals("5S", event.getOpportunity().getSetupHorizon());
        assertNotNull(event.getOpportunity().getEvidenceStrength());
        assertFalse(event.getOpportunity().getInvalidationCodes().isEmpty());

        // lineage: source verbatim + explicit interpretation configuration identity
        assertEquals(featureEventId, event.getFeatureLineage().getSourceFeatureEventId());
        assertEquals(1, event.getFeatureLineage().getSourceFeatureSchemaVersion());
        assertEquals("mfs-features-v2", event.getFeatureLineage().getSourceFeatureSetVersion());
        assertEquals("cfg-e2e", event.getFeatureLineage().getSourceFeatureConfigHash());
        assertEquals(evaluationTs, event.getFeatureLineage().getSourceEvaluationTs());
        assertEquals("TRADE", event.getFeatureLineage().getSourceTriggerSource());
        assertEquals("mse-interpretation-v1", event.getInterpretationLineage().getInterpretationVersion());
        assertEquals("cfg-e2e-interpretation-1", event.getInterpretationLineage().getInterpretationConfigHash());
    }

    @Test
    void duplicateInputYieldsTheSameDeterministicInterpretationSnapshotId() throws Exception {
        String featureEventId = "feat-dup-" + UUID.randomUUID();
        MarketFeaturesSnapshotEvent input =
                bullishContinuationEvent(featureEventId, "mfs-features-v2", System.currentTimeMillis() - 150);

        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);
        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);

        List<MarketInterpretationSnapshotEvent> outputs = awaitInterpretations(featureEventId, 2);
        assertEquals(2, outputs.size());
        assertEquals(outputs.get(0).getMetadata().getEventId(), outputs.get(1).getMetadata().getEventId(),
                "re-processing the same feature snapshot must produce the same interpretationSnapshotId");
    }

    @Test
    void unsupportedFeatureSetVersionIsDeadLetteredNotPublished() throws Exception {
        String featureEventId = "feat-bad-" + UUID.randomUUID();
        MarketFeaturesSnapshotEvent input =
                bullishContinuationEvent(featureEventId, "mfs-features-v99", System.currentTimeMillis() - 150);

        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, Object> dead = awaitDlt(featureEventId);
        assertNotNull(dead);
        assertEquals("binance:spot:BTCUSDT", dead.key());
        assertFalse(interpretationPublishedFor(featureEventId, Duration.ofSeconds(2)),
                "a rejected input must not produce an interpretation snapshot");
        assertTrue(meterRegistry.find("mse.dlt.records")
                .tag("exception", "InvalidMarketFeaturesSnapshotException").counter().count() >= 1.0);
    }

    @Test
    void blockedQualityIsPublishedAsBlockedNotDropped() throws Exception {
        String featureEventId = "feat-unsafe-" + UUID.randomUUID();
        long evaluationTs = System.currentTimeMillis() - 150;
        // build a fresh unsafe variant (Avro objects are mutable; adjust quality in place)
        MarketFeaturesSnapshotEvent input = bullishContinuationEvent(featureEventId, "mfs-features-v2", evaluationTs);
        input.getQuality().setStatus(FeatureQualityStatus.UNSAFE);
        input.getQuality().setSourceOrderBookTrusted(false);

        producer.send(INPUT_TOPIC, input.getMetadata().getInstrumentId(), input).get(10, TimeUnit.SECONDS);

        MarketInterpretationSnapshotEvent event = awaitInterpretation(featureEventId).value();
        assertEquals("BLOCKED", event.getQuality().getStatus());
        assertFalse(event.getQuality().getEligibleForTrading());
        assertEquals("BLOCKED", event.getOpportunity().getStatus());
        assertEquals("NONE", event.getOpportunity().getSide());
        assertNull(event.getOpportunity().getSetupHorizon());
    }

    // ------------------------------------------------------------------ helpers

    private ConsumerRecord<String, MarketInterpretationSnapshotEvent> awaitInterpretation(String featureEventId) {
        return awaitInterpretationRecords(featureEventId, 1).getFirst();
    }

    private List<MarketInterpretationSnapshotEvent> awaitInterpretations(String featureEventId, int count) {
        return awaitInterpretationRecords(featureEventId, count).stream().map(ConsumerRecord::value).toList();
    }

    private List<ConsumerRecord<String, MarketInterpretationSnapshotEvent>> awaitInterpretationRecords(
            String featureEventId, int count) {
        List<ConsumerRecord<String, MarketInterpretationSnapshotEvent>> found = new ArrayList<>();
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline && found.size() < count) {
            ConsumerRecords<String, MarketInterpretationSnapshotEvent> records =
                    interpretationsConsumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, MarketInterpretationSnapshotEvent> r : records) {
                if (featureEventId.equals(r.value().getFeatureLineage().getSourceFeatureEventId())) {
                    found.add(r);
                }
            }
        }
        assertEquals(count, found.size(),
                "expected " + count + " interpretation(s) for " + featureEventId + " within " + WAIT);
        return found;
    }

    private boolean interpretationPublishedFor(String featureEventId, Duration window) {
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, MarketInterpretationSnapshotEvent> r
                    : interpretationsConsumer.poll(Duration.ofMillis(200))) {
                if (featureEventId.equals(r.value().getFeatureLineage().getSourceFeatureEventId())) {
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

    /**
     * A complete, contract-valid, fresh MFS v2 TRADE-triggered snapshot whose flow (all windows),
     * momentum (5S/15S/60S), volatility and 1S book all read bullish/normal — a full
     * momentum-continuation candidate under the configured interpretation policies.
     */
    private static MarketFeaturesSnapshotEvent bullishContinuationEvent(String eventId, String featureSetVersion,
                                                                        long evaluationTs) {
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
                        .setExchangeTs(evaluationTs)
                        .setReceivedTs(evaluationTs + 20)
                        .setProcessedTs(evaluationTs + 30)
                        .build())
                .setComputedTs(evaluationTs + 35)
                // MFS v2: a TRADE-triggered snapshot evaluates as-of the trigger exchangeTs
                .setEvaluationTs(evaluationTs)
                .setFeatureSetVersion(featureSetVersion)
                .setTriggerSource("TRADE")
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
                        .setWarmingUp(false)
                        .setFutureEventDetected(false)
                        .build())
                .setDiagnostics(DiagnosticsEvent.newBuilder().setTotalFeatureGroups(4).build())
                .setSourceState(FeatureSourceStateEvent.newBuilder().setPublishedDepth(20).build())
                .setBbo(BboFeaturesEvent.newBuilder()
                        .setBestBidPrice("50000.0").setBestAskPrice("50005.0")
                        .setBestBidQty("1.5").setBestAskQty("2.0")
                        .setSpreadAbs("5.0").setSpreadBps("1.0").setMidPrice("50002.5")
                        .setMicropriceTop1("50003.1").setMicropriceOffsetBps("6")
                        .build())
                .setBook(BookFeaturesEvent.newBuilder()
                        .setLevelsUsed(5).setTop1Imbalance("0.10").setTop5Imbalance("0.75")
                        .setBidLiquidityTop5("12.5").setAskLiquidityTop5("10.0")
                        .build())
                .setTradeFlow(TradeFlowFeaturesEvent.newBuilder()
                        .setLastTradePrice("50003.0")
                        .setTradeCount1s(20).setValidQtyTradeCount1s(20)
                        .setAggressiveTradeCount1s(15).setUnknownSideCount1s(0)
                        .setSignedFlowImbalance1s("0.60").setTradeIntensity1s("3.0")
                        .setTradeCount5s(50).setValidQtyTradeCount5s(50)
                        .setAggressiveTradeCount5s(40).setUnknownSideCount5s(0)
                        .setSignedFlowImbalance5s("0.60")
                        .setBuyAggressiveVolume5s("4.0").setSellAggressiveVolume5s("2.0")
                        .setTradeCount15s(150).setValidQtyTradeCount15s(150)
                        .setAggressiveTradeCount15s(120).setUnknownSideCount15s(0)
                        .setSignedFlowImbalance15s("0.60")
                        .setTradeCount60s(600).setValidQtyTradeCount60s(600)
                        .setAggressiveTradeCount60s(480).setUnknownSideCount60s(0)
                        .setSignedFlowImbalance60s("0.60")
                        .build())
                .setRegime(ShortTermRegimeFeaturesEvent.newBuilder()
                        .setLastTradeDistanceToMidBps("0.4")
                        .setRealizedVolatilityBps1s("5.0")
                        .setRealizedVolatilityBps5s("5.0")
                        .setRealizedVolatilityBps15s("5.0")
                        .setRealizedVolatilityBps60s("5.0")
                        .setPriceChangeBps5s("6").setPriceChangeBps15s("8").setPriceChangeBps60s("10")
                        .build())
                .build();
    }
}
