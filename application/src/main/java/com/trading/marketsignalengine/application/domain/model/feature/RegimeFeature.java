package com.trading.marketsignalengine.application.domain.model.feature;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * Short-term regime features from MFS v2. Volatilities are realized log-return volatility in basis
 * points per window; {@code priceChangeBps*} is the signed mid-price move over the window;
 * {@code highLowRangeBps60s} is the 60s high-low range. Only {@code realizedVolatilityBps1s} drives a
 * gate today; the deprecated upstream alias {@code shortTermVolatility1s} is intentionally not mapped.
 */
@Builder(toBuilder = true)
public record RegimeFeature(
        BigDecimal lastTradeDistanceToMidBps,
        BigDecimal realizedVolatilityBps1s,
        BigDecimal realizedVolatilityBps5s,
        BigDecimal realizedVolatilityBps15s,
        BigDecimal realizedVolatilityBps60s,
        BigDecimal priceChangeBps5s,
        BigDecimal priceChangeBps15s,
        BigDecimal priceChangeBps60s,
        BigDecimal highLowRangeBps60s) {
}
