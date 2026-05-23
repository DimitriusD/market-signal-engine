package com.trading.marketsignalengine.application.domain.service;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalEvaluationContext;

public interface MarketSignalEngine {

    MarketSignalSnapshot evaluate(SignalEvaluationContext context);
}
