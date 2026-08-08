package com.trading.marketsignalengine.application.domain.model;

public enum SignalType {
    DATA_TRADABLE,
    NO_TRADE_QUALITY_MISSING,
    NO_TRADE_OUT_OF_SYNC,
    NO_TRADE_RECOVERING_BOOK,
    NO_TRADE_STALE_BOOK,
    NO_TRADE_STALE_TRADES,
    NO_TRADE_VOLATILITY_MISSING,
    NO_TRADE_SPREAD_MISSING,
    NO_TRADE_INCOMPLETE_BOOK,
    SPREAD_ACCEPTABLE,
    SPREAD_TOO_WIDE,
    BUY_PRESSURE,
    SELL_PRESSURE,
    TRADE_FLOW_NEUTRAL,
    ORDER_BOOK_BULLISH,
    ORDER_BOOK_BEARISH,
    ORDER_BOOK_NEUTRAL,
    VOLATILITY_NORMAL,
    VOLATILITY_HIGH,
    // Invalid (impossible) feature-value guards. These are RISK_OFF: a semantically broken feature
    // value (crossed BBO, out-of-range imbalance, negative volatility, ...) must yield no-trade rather
    // than a bullish/bearish/normal verdict computed on garbage input.
    NO_TRADE_INVALID_BBO,
    NO_TRADE_INVALID_ORDER_BOOK,
    NO_TRADE_INVALID_TRADE_FLOW,
    NO_TRADE_INVALID_VOLATILITY,
    NO_TRADE_INVALID_FEATURE_SNAPSHOT,
    // Reserved for a future regime classifier over windowed features (returns, range expansion,
    // trend persistence). Not currently produced: lastTradeDistanceToMidBps is microstructure, not
    // a trend, so no rule emits these yet.
    REGIME_TRENDING_UP,
    REGIME_TRENDING_DOWN,
    REGIME_RANGING,
    LONG_SETUP_FORMING,
    SHORT_SETUP_FORMING,
    MARKET_MIXED,
    NO_TRADE_CONDITION
}
