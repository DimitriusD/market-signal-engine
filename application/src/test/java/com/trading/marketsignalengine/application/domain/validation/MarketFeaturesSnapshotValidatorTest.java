package com.trading.marketsignalengine.application.domain.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MarketFeaturesSnapshotValidatorTest {

    private final MarketFeaturesSnapshotValidator validator = new MarketFeaturesSnapshotValidator();

    @Test
    void validSnapshotPasses() {
        assertDoesNotThrow(() -> validator.validate(validBuilder().build()));
    }

    @Test
    void nullSnapshotFails() {
        assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> validator.validate(null));
    }

    @Test
    void blankSnapshotIdFails() {
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> validator.validate(validBuilder().snapshotId(" ").build()));
    }

    @Test
    void blankInstrumentIdFails() {
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> validator.validate(validBuilder().instrumentId("").build()));
    }

    @Test
    void blankFeatureSetVersionFails() {
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> validator.validate(validBuilder().featureSetVersion(null).build()));
    }

    @Test
    void nullEventTimeFails() {
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> validator.validate(validBuilder().eventTime(null).build()));
    }

    private static MarketFeaturesSnapshot.MarketFeaturesSnapshotBuilder validBuilder() {
        return MarketFeaturesSnapshot.builder()
                .snapshotId("snap-1")
                .instrumentId("binance:spot:BTCUSDT")
                .featureSetVersion("mfs-core-v1")
                .eventTime(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
