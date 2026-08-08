package com.trading.marketsignalengine.application.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.trading.marketsignalengine.application.domain.model.MarketSetup;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SetupSide;
import com.trading.marketsignalengine.application.domain.model.SetupType;
import com.trading.marketsignalengine.application.domain.model.SignalStrength;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SetupResolverTest {

    private final SetupResolver resolver = new SetupResolver();

    @Test
    void riskOffResolvesToNone() {
        MarketSetup setup = resolver.resolve(
                List.of(longSetup()),
                RiskLevel.NO_TRADE);

        assertEquals(SetupSide.NONE, setup.side());
        assertEquals(SetupType.NONE, setup.type());
    }

    @Test
    void riskOffSignalResolvesToNoneEvenWhenRiskLevelNotNoTrade() {
        MarketSetup setup = resolver.resolve(
                List.of(longSetup(), riskOff()),
                RiskLevel.NORMAL);

        assertEquals(SetupSide.NONE, setup.side());
    }

    @Test
    void longSetupResolvesToLong() {
        MarketSetup setup = resolver.resolve(List.of(longSetup()), RiskLevel.NORMAL);

        assertEquals(SetupSide.LONG, setup.side());
        assertEquals(SetupType.MICROSTRUCTURE_MOMENTUM, setup.type());
        assertEquals(SignalStrength.STRONG, setup.strength());
        assertEquals(0, setup.confidence().compareTo(new BigDecimal("0.75")));
    }

    @Test
    void shortSetupResolvesToShort() {
        MarketSetup setup = resolver.resolve(List.of(shortSetup()), RiskLevel.NORMAL);

        assertEquals(SetupSide.SHORT, setup.side());
        assertEquals(SetupType.MICROSTRUCTURE_MOMENTUM, setup.type());
    }

    @Test
    void conflictingSetupsResolveToNone() {
        MarketSetup setup = resolver.resolve(List.of(longSetup(), shortSetup()), RiskLevel.NORMAL);

        assertEquals(SetupSide.NONE, setup.side());
        assertEquals("Conflicting long and short setups", setup.reason());
    }

    @Test
    void noSetupResolvesToNone() {
        MarketSetup setup = resolver.resolve(
                List.of(MarketSignal.bullish(SignalType.BUY_PRESSURE, SignalStrength.STRONG,
                        BigDecimal.ONE, "buy", null)),
                RiskLevel.NORMAL);

        assertEquals(SetupSide.NONE, setup.side());
    }

    private static MarketSignal longSetup() {
        return MarketSignal.bullish(SignalType.LONG_SETUP_FORMING, SignalStrength.STRONG,
                new BigDecimal("0.75"), "long", null);
    }

    private static MarketSignal shortSetup() {
        return MarketSignal.bearish(SignalType.SHORT_SETUP_FORMING, SignalStrength.STRONG,
                new BigDecimal("0.75"), "short", null);
    }

    private static MarketSignal riskOff() {
        return MarketSignal.riskOff(SignalType.NO_TRADE_CONDITION, SignalStrength.EXTREME,
                BigDecimal.ONE, "risk off", null);
    }
}
