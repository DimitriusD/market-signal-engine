package com.trading.marketsignalengine.application.domain.model.feature;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record BookFeature(
        int levelsUsed,
        BigDecimal bidLiquidityTop5,
        BigDecimal askLiquidityTop5,
        BigDecimal top1Imbalance,
        BigDecimal top5Imbalance,
        BigDecimal bestBidGapTicks,
        BigDecimal bestAskGapTicks) {
}
