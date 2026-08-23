package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * Side of the interpreted opportunity (contract: {@code MarketOpportunityEvent.side}). {@code LONG} means
 * "a long opportunity pattern is present on the market", <b>not</b> "buy now"; there is deliberately no
 * BUY/SELL, quantity, price or execution semantics anywhere in this model.
 */
public enum OpportunitySide {
    LONG,
    SHORT,
    NONE;

    public boolean isDirectional() {
        return this == LONG || this == SHORT;
    }
}
