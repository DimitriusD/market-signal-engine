package com.trading.marketsignalengine.config;

import com.trading.marketsignalengine.application.domain.rule.CompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.DefaultCompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.OrderBookSignalRule;
import com.trading.marketsignalengine.application.domain.rule.QualitySignalRule;
import com.trading.marketsignalengine.application.domain.rule.RegimeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.SignalRule;
import com.trading.marketsignalengine.application.domain.rule.SpreadSignalRule;
import com.trading.marketsignalengine.application.domain.rule.TradeFlowSignalRule;
import com.trading.marketsignalengine.application.domain.rule.VolatilitySignalRule;
import com.trading.marketsignalengine.application.domain.service.DefaultMarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.MarketSignalEngine;
import com.trading.marketsignalengine.application.domain.service.SignalAggregator;
import com.trading.marketsignalengine.application.port.input.EvaluateMarketSignalsUseCase;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import com.trading.marketsignalengine.application.port.output.SignalConfigurationProviderPort;
import com.trading.marketsignalengine.application.service.MarketSignalEvaluationService;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SignalProperties.class)
public class InfrastructureConfig {

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
    public EvaluateMarketSignalsUseCase evaluateMarketSignalsUseCase(
            MarketSignalEngine marketSignalEngine,
            SignalConfigurationProviderPort configurationProvider,
            MarketSignalSnapshotPublisherPort publisher) {
        return new MarketSignalEvaluationService(marketSignalEngine, configurationProvider, publisher);
    }
}
