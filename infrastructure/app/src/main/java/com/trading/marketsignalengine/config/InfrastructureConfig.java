package com.trading.marketsignalengine.config;

import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.StandardSignalEngine;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.application.port.output.SignalMetricsPort;
import com.trading.marketsignalengine.application.service.MarketSignalHandleService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(SignalProperties.class)
public class InfrastructureConfig {

    @Bean
    public SignalConfiguration signalConfiguration(SignalProperties properties) {
        return SignalConfiguration.builder()
                .signalSetVersion(defaultString(properties.getSignalSetVersion(), "mse-signals-v8"))
                .maxSpreadBps(defaultDecimal(properties.getMaxSpreadBps(), "2.0"))
                .buyFlowImbalance5sThreshold(defaultDecimal(properties.getBuyFlowImbalance5sThreshold(), "0.15"))
                .sellFlowImbalance5sThreshold(defaultDecimal(properties.getSellFlowImbalance5sThreshold(), "-0.15"))
                .minTradeCount5sForTradeFlowSignal(
                        defaultInteger(properties.getMinTradeCount5sForTradeFlowSignal(), 10))
                .buyBookImbalanceThreshold(defaultDecimal(properties.getBuyBookImbalanceThreshold(), "0.60"))
                .sellBookImbalanceThreshold(defaultDecimal(properties.getSellBookImbalanceThreshold(), "-0.60"))
                .maxRealizedVolatilityBps1s(defaultDecimal(properties.getMaxRealizedVolatilityBps1s(), "50.0"))
                .microstructureSetupTtlMs(defaultLong(properties.getMicrostructureSetupTtlMs(), 2_000L))
                .riskOffTtlMs(defaultLong(properties.getRiskOffTtlMs(), 5_000L))
                .neutralTtlMs(defaultLong(properties.getNeutralTtlMs(), 1_000L))
                .build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The live engine is built by {@link StandardSignalEngine} — the same factory the replay harness
     * and golden tests use — so production wiring and replayed wiring cannot drift apart.
     */
    @Bean
    public MarketSignalEngine marketSignalEngine(SignalConfiguration signalConfiguration, Clock clock) {
        return StandardSignalEngine.create(signalConfiguration, clock);
    }

    @Bean
    public MarketFeaturesSnapshotValidator marketFeaturesSnapshotValidator(SignalProperties properties) {
        List<String> configured = properties.getSupportedFeatureSetVersions();
        Set<String> supported = configured == null || configured.isEmpty()
                ? Set.of("mfs-features-v2")
                : new LinkedHashSet<>(configured);
        return new MarketFeaturesSnapshotValidator(supported);
    }

    @Bean
    public MarketFeaturesHandler marketFeatureHandler(
            MarketSignalEngine marketSignalEngine,
            MarketSignalSnapshotPublisherPort publisher,
            MarketFeaturesSnapshotValidator validator,
            SignalMetricsPort metrics) {
        return new MarketSignalHandleService(marketSignalEngine, publisher, validator, metrics);
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static BigDecimal defaultDecimal(BigDecimal value, String fallback) {
        return value == null ? new BigDecimal(fallback) : value;
    }

    private static int defaultInteger(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static long defaultLong(Long value, long fallback) {
        return value == null ? fallback : value;
    }
}
