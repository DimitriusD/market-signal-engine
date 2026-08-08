package com.trading.marketsignalengine.application.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.trading.marketsignalengine.application.domain.model.MarketSetup;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SetupSide;
import com.trading.marketsignalengine.application.domain.model.SetupType;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalValidity;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SignalValidityResolverTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private final SignalValidityResolver resolver = new SignalValidityResolver();
    private final SignalConfiguration configuration = SignalConfiguration.defaults();

    @Test
    void noTradeUsesRiskOffTtl() {
        SignalValidity validity = resolver.resolve(context(), RiskLevel.NO_TRADE, MarketSetup.none("no trade"));

        assertEquals(configuration.riskOffTtlMs(), validity.ttlMs());
        assertEquals(EVALUATED_AT.plusMillis(configuration.riskOffTtlMs()), validity.validUntil());
    }

    @Test
    void longSetupUsesMicrostructureSetupTtl() {
        SignalValidity validity = resolver.resolve(context(), RiskLevel.NORMAL, setup(SetupSide.LONG));

        assertEquals(configuration.microstructureSetupTtlMs(), validity.ttlMs());
        assertEquals(EVALUATED_AT.plusMillis(configuration.microstructureSetupTtlMs()), validity.validUntil());
    }

    @Test
    void shortSetupUsesMicrostructureSetupTtl() {
        SignalValidity validity = resolver.resolve(context(), RiskLevel.NORMAL, setup(SetupSide.SHORT));

        assertEquals(configuration.microstructureSetupTtlMs(), validity.ttlMs());
    }

    @Test
    void neutralUsesNeutralTtl() {
        SignalValidity validity = resolver.resolve(context(), RiskLevel.NORMAL, MarketSetup.none("neutral"));

        assertEquals(configuration.neutralTtlMs(), validity.ttlMs());
        assertEquals(EVALUATED_AT.plusMillis(configuration.neutralTtlMs()), validity.validUntil());
    }

    private static SignalEvaluationContext context() {
        return new SignalEvaluationContext(
                SignalRuleTestSupport.defaultFeatures(),
                SignalConfiguration.defaults(),
                EVALUATED_AT);
    }

    private static MarketSetup setup(SetupSide side) {
        return new MarketSetup(side, SetupType.MICROSTRUCTURE_MOMENTUM, SignalStrength.STRONG,
                new BigDecimal("0.75"), "setup", null);
    }
}
