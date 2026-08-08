package com.trading.marketsignalengine.config;

import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.rule.*;
import com.trading.marketsignalengine.application.domain.service.DefaultMarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.SetupResolver;
import com.trading.marketsignalengine.application.domain.service.SignalAggregator;
import com.trading.marketsignalengine.application.domain.service.SignalValidityResolver;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.application.service.MarketSignalHandleService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Configuration
@EnableConfigurationProperties(SignalProperties.class)
public class InfrastructureConfig {

    @Bean
    public SignalConfiguration signalConfiguration(SignalProperties properties) {
        return SignalConfiguration.builder()
                .signalSetVersion(defaultString(properties.getSignalSetVersion(), "mse-signals-v7"))
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
    public QualitySignalRule qualitySignalRule() {
        return new QualitySignalRule();
    }

    @Bean
    public SpreadSignalRule spreadSignalRule() {
        return new SpreadSignalRule();
    }

    @Bean
    public TradeFlowSignalRule tradeFlowSignalRule() {
        return new TradeFlowSignalRule();
    }

    @Bean
    public OrderBookSignalRule orderBookSignalRule() {
        return new OrderBookSignalRule();
    }

    @Bean
    public VolatilitySignalRule volatilitySignalRule() {
        return new VolatilitySignalRule();
    }

    @Bean
    public CompositeSignalRule compositeSignalRule() {
        return new DefaultCompositeSignalRule();
    }

    @Bean
    public SetupResolver setupResolver() {
        return new SetupResolver();
    }

    @Bean
    public SignalValidityResolver signalValidityResolver() {
        return new SignalValidityResolver();
    }

    @Bean
    public SignalAggregator signalAggregator(
            SetupResolver setupResolver,
            SignalValidityResolver signalValidityResolver) {
        return new SignalAggregator(setupResolver, signalValidityResolver);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public MarketSignalEngine marketSignalEngine(
            QualitySignalRule qualitySignalRule,
            SpreadSignalRule spreadSignalRule,
            TradeFlowSignalRule tradeFlowSignalRule,
            OrderBookSignalRule orderBookSignalRule,
            VolatilitySignalRule volatilitySignalRule,
            CompositeSignalRule compositeSignalRule,
            SignalAggregator signalAggregator,
            SignalConfiguration signalConfiguration,
            Clock clock) {
        List<SignalRule> qualityGateRules = List.of(qualitySignalRule);
        List<SignalRule> tradabilityGateRules = List.of(
                spreadSignalRule,
                volatilitySignalRule);
        // Regime is intentionally not a directional rule: lastTradeDistanceToMidBps is point-in-time
        // microstructure, not a trend. A real regime classifier belongs over windowed features. See
        // DirectionalReduction#DIRECTIONAL_BASE_TYPES, which also excludes REGIME from the score.
        List<SignalRule> directionalRules = List.of(
                tradeFlowSignalRule,
                orderBookSignalRule);
        return new DefaultMarketSignalEngine(
                qualityGateRules,
                tradabilityGateRules,
                directionalRules,
                compositeSignalRule,
                signalAggregator,
                signalConfiguration,
                clock);
    }

    @Bean
    public MarketFeaturesSnapshotValidator marketFeaturesSnapshotValidator() {
        return new MarketFeaturesSnapshotValidator();
    }

    @Bean
    public MarketFeaturesHandler marketFeatureHandler(
            MarketSignalEngine marketSignalEngine,
            MarketSignalSnapshotPublisherPort publisher,
            MarketFeaturesSnapshotValidator validator) {
        return new MarketSignalHandleService(marketSignalEngine, publisher, validator);
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
