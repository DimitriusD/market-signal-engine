package com.trading.marketsignalengine.config;

import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.rule.*;
import com.trading.marketsignalengine.application.domain.service.DefaultMarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.SignalAggregator;
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
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SignalConfiguration signalConfiguration(SignalProperties properties) {
        return SignalConfiguration.builder()
                .signalSetVersion(defaultString(properties.getSignalSetVersion(), "mse-signals-v1"))
                .maxSpreadBps(defaultDecimal(properties.getMaxSpreadBps(), "2.0"))
                .buySignedTradeFlow5sThreshold(defaultDecimal(properties.getBuySignedTradeFlow5sThreshold(), "0.0"))
                .sellSignedTradeFlow5sThreshold(defaultDecimal(properties.getSellSignedTradeFlow5sThreshold(), "0.0"))
                .buyBookImbalanceThreshold(defaultDecimal(properties.getBuyBookImbalanceThreshold(), "0.60"))
                .sellBookImbalanceThreshold(defaultDecimal(properties.getSellBookImbalanceThreshold(), "-0.60"))
                .maxShortTermVolatility1s(defaultDecimal(properties.getMaxShortTermVolatility1s(), "0.01"))
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
    public RegimeSignalRule regimeSignalRule() {
        return new RegimeSignalRule();
    }

    @Bean
    public CompositeSignalRule compositeSignalRule() {
        return new DefaultCompositeSignalRule();
    }

    @Bean
    public SignalAggregator signalAggregator() {
        return new SignalAggregator();
    }

    @Bean
    public MarketSignalEngine marketSignalEngine(
            QualitySignalRule qualitySignalRule,
            SpreadSignalRule spreadSignalRule,
            TradeFlowSignalRule tradeFlowSignalRule,
            OrderBookSignalRule orderBookSignalRule,
            VolatilitySignalRule volatilitySignalRule,
            RegimeSignalRule regimeSignalRule,
            CompositeSignalRule compositeSignalRule,
            SignalAggregator signalAggregator) {
        List<SignalRule> baseRules = List.of(
                qualitySignalRule,
                spreadSignalRule,
                tradeFlowSignalRule,
                orderBookSignalRule,
                volatilitySignalRule,
                regimeSignalRule);
        return new DefaultMarketSignalEngine(baseRules, compositeSignalRule, signalAggregator);
    }

    @Bean
    public MarketSignalHandleService marketSignalEvaluationService(
            MarketSignalEngine marketSignalEngine,
            SignalConfiguration signalConfiguration,
            MarketSignalSnapshotPublisherPort publisher,
            Clock clock) {
        return new MarketSignalHandleService(marketSignalEngine, signalConfiguration, publisher, clock);
    }

    @Bean
    public MarketFeaturesHandler marketFeatureHandler(
            MarketSignalHandleService marketSignalHandleService) {
        return marketSignalHandleService;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static BigDecimal defaultDecimal(BigDecimal value, String fallback) {
        return value == null ? new BigDecimal(fallback) : value;
    }
}
