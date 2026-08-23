package com.trading.marketsignalengine.application.domain.interpretation.book;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNotPlaceholder;

import java.math.BigDecimal;

/**
 * Versioned, immutable Book V1 policy. Book evidence is instantaneous (1S only), so there are no
 * per-horizon policies — one set of parameters for the single scoped horizon. Exact
 * {@link BigDecimal} only — no {@code double}.
 *
 * <p>Invariants: {@code policyVersion} non-blank and not a placeholder; {@code minimumLevelsUsed > 0};
 * {@code bullishTop5ImbalanceThreshold ∈ (0, 1]}, {@code bearishTop5ImbalanceThreshold ∈ [-1, 0)}
 * (a dead zone always exists around zero); {@code bullishMicropriceOffsetBpsThreshold > 0},
 * {@code bearishMicropriceOffsetBpsThreshold < 0}; {@code fullStrengthAbsMicropriceOffsetBps > 0};
 * {@code maxSafeAbsMicropriceOffsetBps > fullStrengthAbsMicropriceOffsetBps}; and full strength is
 * only reached at or beyond both microprice thresholds.
 *
 * <p>Deliberately absent: {@code maxSpreadBps}. A wide but structurally valid spread is not bearish,
 * not bullish and never decides the book direction — spread acceptance is a later execution /
 * liquidity gate, not evidence. Production values are a replay-driven decision of a later stage.
 */
public record BookAssessmentPolicy(
        String policyVersion,
        int minimumLevelsUsed,
        BigDecimal bullishTop5ImbalanceThreshold,
        BigDecimal bearishTop5ImbalanceThreshold,
        BigDecimal bullishMicropriceOffsetBpsThreshold,
        BigDecimal bearishMicropriceOffsetBpsThreshold,
        BigDecimal fullStrengthAbsMicropriceOffsetBps,
        BigDecimal maxSafeAbsMicropriceOffsetBps) {

    private static final BigDecimal MINUS_ONE = BigDecimal.ONE.negate();

    public BookAssessmentPolicy {
        requireNotPlaceholder(policyVersion, "book policyVersion");
        require(minimumLevelsUsed > 0, "book minimumLevelsUsed must be positive, got " + minimumLevelsUsed);
        requireNonNull(bullishTop5ImbalanceThreshold, "book bullishTop5ImbalanceThreshold");
        requireNonNull(bearishTop5ImbalanceThreshold, "book bearishTop5ImbalanceThreshold");
        requireNonNull(bullishMicropriceOffsetBpsThreshold, "book bullishMicropriceOffsetBpsThreshold");
        requireNonNull(bearishMicropriceOffsetBpsThreshold, "book bearishMicropriceOffsetBpsThreshold");
        requireNonNull(fullStrengthAbsMicropriceOffsetBps, "book fullStrengthAbsMicropriceOffsetBps");
        requireNonNull(maxSafeAbsMicropriceOffsetBps, "book maxSafeAbsMicropriceOffsetBps");
        require(bullishTop5ImbalanceThreshold.signum() > 0
                        && bullishTop5ImbalanceThreshold.compareTo(BigDecimal.ONE) <= 0,
                "book bullishTop5ImbalanceThreshold must be within (0, 1], got "
                        + bullishTop5ImbalanceThreshold.toPlainString());
        require(bearishTop5ImbalanceThreshold.signum() < 0
                        && bearishTop5ImbalanceThreshold.compareTo(MINUS_ONE) >= 0,
                "book bearishTop5ImbalanceThreshold must be within [-1, 0), got "
                        + bearishTop5ImbalanceThreshold.toPlainString());
        require(bullishMicropriceOffsetBpsThreshold.signum() > 0,
                "book bullishMicropriceOffsetBpsThreshold must be positive, got "
                        + bullishMicropriceOffsetBpsThreshold.toPlainString());
        require(bearishMicropriceOffsetBpsThreshold.signum() < 0,
                "book bearishMicropriceOffsetBpsThreshold must be negative, got "
                        + bearishMicropriceOffsetBpsThreshold.toPlainString());
        require(fullStrengthAbsMicropriceOffsetBps.signum() > 0,
                "book fullStrengthAbsMicropriceOffsetBps must be positive, got "
                        + fullStrengthAbsMicropriceOffsetBps.toPlainString());
        require(maxSafeAbsMicropriceOffsetBps.compareTo(fullStrengthAbsMicropriceOffsetBps) > 0,
                "book maxSafeAbsMicropriceOffsetBps must be strictly above fullStrengthAbsMicropriceOffsetBps");
        require(fullStrengthAbsMicropriceOffsetBps.compareTo(bullishMicropriceOffsetBpsThreshold) >= 0,
                "book fullStrengthAbsMicropriceOffsetBps must be at least bullishMicropriceOffsetBpsThreshold");
        require(fullStrengthAbsMicropriceOffsetBps.compareTo(bearishMicropriceOffsetBpsThreshold.abs()) >= 0,
                "book fullStrengthAbsMicropriceOffsetBps must be at least abs(bearishMicropriceOffsetBpsThreshold)");
    }
}
