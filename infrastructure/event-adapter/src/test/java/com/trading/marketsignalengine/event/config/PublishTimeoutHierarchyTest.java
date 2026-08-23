package com.trading.marketsignalengine.event.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishTimeoutHierarchyTest {

    @Test
    void recommendedDefaultsAreValid() {
        PublishTimeoutHierarchy h = new PublishTimeoutHierarchy(3_000L, 5_000L, 6_500L).validate();

        assertEquals(3_000L, h.requestTimeoutMs());
        assertEquals(5_000L, h.deliveryTimeoutMs());
        assertEquals(6_500L, h.publishTimeoutMs());
    }

    @Test
    void readsProducerPropertiesAsStringsOrNumbersAndFallsBackToKafkaDefaults() {
        PublishTimeoutHierarchy fromStrings = PublishTimeoutHierarchy.from(
                Map.of("request.timeout.ms", "3000", "delivery.timeout.ms", "5000"), 6_500L);
        PublishTimeoutHierarchy fromNumbers = PublishTimeoutHierarchy.from(
                Map.of("request.timeout.ms", 3000, "delivery.timeout.ms", 5000L), 6_500L);
        PublishTimeoutHierarchy defaults = PublishTimeoutHierarchy.from(Map.of(), 130_000L);

        assertEquals(fromStrings, fromNumbers);
        assertEquals(PublishTimeoutHierarchy.KAFKA_DEFAULT_REQUEST_TIMEOUT_MS, defaults.requestTimeoutMs());
        assertEquals(PublishTimeoutHierarchy.KAFKA_DEFAULT_DELIVERY_TIMEOUT_MS, defaults.deliveryTimeoutMs());
        defaults.validate();
    }

    @Test
    void kafkaDefaultsWithTheOldApplicationTimeoutAreRejected() {
        // the historical bug shape: app waits 5 s while the producer keeps delivering for 120 s
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PublishTimeoutHierarchy.from(Map.of(), 5_000L).validate());
        assertTrue(ex.getMessage().contains("delivery.timeout.ms=120000"), ex.getMessage());
    }

    @Test
    void requestMustBeStrictlyBelowDelivery() {
        assertThrows(IllegalStateException.class, () -> new PublishTimeoutHierarchy(5_000L, 5_000L, 6_500L).validate());
        assertThrows(IllegalStateException.class, () -> new PublishTimeoutHierarchy(6_000L, 5_000L, 6_500L).validate());
    }

    @Test
    void deliveryMustBeStrictlyBelowPublish() {
        assertThrows(IllegalStateException.class, () -> new PublishTimeoutHierarchy(3_000L, 5_000L, 5_000L).validate());
        assertThrows(IllegalStateException.class, () -> new PublishTimeoutHierarchy(3_000L, 10_000L, 5_000L).validate());
    }

    @Test
    void allValuesMustBePositive() {
        assertThrows(IllegalStateException.class, () -> new PublishTimeoutHierarchy(0L, 5_000L, 6_500L).validate());
        assertThrows(IllegalStateException.class, () -> new PublishTimeoutHierarchy(3_000L, -1L, 6_500L).validate());
        assertThrows(IllegalStateException.class, () -> new PublishTimeoutHierarchy(3_000L, 5_000L, 0L).validate());
    }

    @Test
    void nonNumericProducerPropertyIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> PublishTimeoutHierarchy.from(Map.of("delivery.timeout.ms", "five"), 6_500L));
    }
}
