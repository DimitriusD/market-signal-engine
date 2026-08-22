package com.trading.marketsignalengine.application.domain.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarketFeaturesSnapshotValidatorTest {

    private final MarketFeaturesSnapshotValidator validator =
            new MarketFeaturesSnapshotValidator(Set.of("mfs-features-v2"));

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

    @Test
    void unsupportedFeatureSetVersionFailsClosed() {
        // An unknown upstream contract version must go to the DLT, not be interpreted on assumptions.
        InvalidMarketFeaturesSnapshotException ex = assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> validator.validate(validBuilder().featureSetVersion("mfs-features-v3").build()));

        assertTrue(ex.getMessage().contains("mfs-features-v3"));
        assertTrue(ex.getMessage().contains("mfs-features-v2"));
    }

    @Test
    void versionMatchIsExactNotPrefix() {
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> validator.validate(validBuilder().featureSetVersion("mfs-features-v2-rc1").build()));
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> validator.validate(validBuilder().featureSetVersion("MFS-FEATURES-V2").build()));
    }

    @Test
    void allowlistCanHoldSeveralVersions() {
        MarketFeaturesSnapshotValidator wide =
                new MarketFeaturesSnapshotValidator(Set.of("mfs-features-v2", " mfs-core-v2 "));

        assertDoesNotThrow(() -> wide.validate(validBuilder().featureSetVersion("mfs-core-v2").build()));
        assertEquals(Set.of("mfs-features-v2", "mfs-core-v2"), wide.supportedFeatureSetVersions());
    }

    @Test
    void emptyOrBlankAllowlistIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new MarketFeaturesSnapshotValidator(Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new MarketFeaturesSnapshotValidator(null));
        assertThrows(IllegalArgumentException.class, () -> new MarketFeaturesSnapshotValidator(Set.of(" ")));
    }

    private static MarketFeaturesSnapshot.MarketFeaturesSnapshotBuilder validBuilder() {
        return MarketFeaturesSnapshot.builder()
                .snapshotId("snap-1")
                .instrumentId("binance:spot:BTCUSDT")
                .featureSetVersion("mfs-features-v2")
                .eventTime(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
