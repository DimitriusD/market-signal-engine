package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.rule.DefaultCompositeSignalRule;
import com.trading.marketsignalengine.application.domain.rule.OrderBookSignalRule;
import com.trading.marketsignalengine.application.domain.rule.QualitySignalRule;
import com.trading.marketsignalengine.application.domain.rule.SignalRule;
import com.trading.marketsignalengine.application.domain.rule.SpreadSignalRule;
import com.trading.marketsignalengine.application.domain.rule.TradeFlowSignalRule;
import com.trading.marketsignalengine.application.domain.rule.VolatilitySignalRule;
import java.time.Clock;
import java.util.List;

/**
 * The one canonical wiring of the production rule set into the three engine phases. Both the live
 * Spring composition root and the replay harness build their engine here, so a replayed snapshot is
 * guaranteed to run through exactly the rules and phase layout that production runs — there is no
 * second, parallel wiring that could drift.
 *
 * <p>Phase layout:
 * <ol>
 *   <li>quality gate: {@link QualitySignalRule}</li>
 *   <li>tradability gate: {@link SpreadSignalRule}, {@link VolatilitySignalRule}</li>
 *   <li>directional: {@link TradeFlowSignalRule}, {@link OrderBookSignalRule};
 *       composite: {@link DefaultCompositeSignalRule}</li>
 * </ol>
 * Regime is intentionally not a directional rule: {@code lastTradeDistanceToMidBps} is point-in-time
 * microstructure, not a trend. A real regime classifier belongs over windowed features. See
 * {@link DirectionalReduction}, which also excludes REGIME from the score.
 */
public final class StandardSignalEngine {

    private StandardSignalEngine() {
    }

    public static DefaultMarketSignalEngine create(SignalConfiguration configuration, Clock clock) {
        List<SignalRule> qualityGateRules = List.of(new QualitySignalRule());
        List<SignalRule> tradabilityGateRules = List.of(
                new SpreadSignalRule(),
                new VolatilitySignalRule());
        List<SignalRule> directionalRules = List.of(
                new TradeFlowSignalRule(),
                new OrderBookSignalRule());
        return new DefaultMarketSignalEngine(
                qualityGateRules,
                tradabilityGateRules,
                directionalRules,
                new DefaultCompositeSignalRule(),
                new SignalAggregator(new SetupResolver(), new SignalValidityResolver()),
                configuration,
                clock);
    }
}
