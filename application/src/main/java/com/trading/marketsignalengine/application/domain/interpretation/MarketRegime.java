package com.trading.marketsignalengine.application.domain.interpretation;

/**
 * Short-term regime label of a horizon or of the cross-horizon view (contract: the open-string
 * {@code regime} fields). In the domain an <em>absent</em> regime ({@code null}) means "not assessed /
 * not eligible"; {@link #UNKNOWN} means "assessed but could not be classified". The two are distinct.
 */
public enum MarketRegime {
    TRENDING,
    RANGING,
    VOLATILE,
    QUIET,
    UNKNOWN
}
