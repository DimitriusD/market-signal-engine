package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.MarketSetup;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.RiskLevel;
import com.trading.marketsignalengine.application.domain.model.SetupSide;
import com.trading.marketsignalengine.application.domain.model.SetupType;
import com.trading.marketsignalengine.application.domain.model.SignalDirection;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import java.util.List;
import java.util.Optional;

/**
 * Derives the snapshot-level {@link MarketSetup} from the resolved signal list. This is a pure
 * projection of the already-computed signals: it never re-evaluates features, it only reads which
 * setup signals were produced and the current {@link RiskLevel}.
 */
public class SetupResolver {

    public MarketSetup resolve(List<MarketSignal> signals, RiskLevel riskLevel) {
        List<MarketSignal> safeSignals = signals == null ? List.of() : signals;

        if (riskLevel == RiskLevel.NO_TRADE) {
            return MarketSetup.none("No setup because risk level is NO_TRADE");
        }

        boolean hasRiskOff = safeSignals.stream()
                .anyMatch(signal -> signal.direction() == SignalDirection.RISK_OFF);
        if (hasRiskOff) {
            return MarketSetup.none("No setup because risk-off signal is active");
        }

        Optional<MarketSignal> longSetup = find(safeSignals, SignalType.LONG_SETUP_FORMING);
        Optional<MarketSignal> shortSetup = find(safeSignals, SignalType.SHORT_SETUP_FORMING);

        if (longSetup.isPresent() && shortSetup.isPresent()) {
            return MarketSetup.none("Conflicting long and short setups");
        }

        if (longSetup.isPresent()) {
            return fromSignal(longSetup.get(), SetupSide.LONG);
        }

        if (shortSetup.isPresent()) {
            return fromSignal(shortSetup.get(), SetupSide.SHORT);
        }

        return MarketSetup.none("No directional setup is currently formed");
    }

    private static Optional<MarketSignal> find(List<MarketSignal> signals, SignalType type) {
        return signals.stream()
                .filter(signal -> signal.type() == type)
                .findFirst();
    }

    private static MarketSetup fromSignal(MarketSignal signal, SetupSide side) {
        return new MarketSetup(
                side,
                SetupType.MICROSTRUCTURE_MOMENTUM,
                signal.strength(),
                signal.confidence(),
                signal.reason(),
                signal.attributes()
        );
    }
}
