package com.trading.marketsignalengine.application.domain.interpretation.book;

import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessmentResolver;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityEligibilityPolicy;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowFeature;
import com.trading.marketsignalengine.application.domain.model.feature.TradeFlowWindow;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Fixtures of the Book V1 tests. The policy is an <b>explicit test fixture</b>, not a production
 * calibration. Snapshots are the contract-valid MFS v2 TRADE-triggered snapshot of
 * {@link SignalRuleTestSupport} with all four trade-flow windows active (so every horizon is ELIGIBLE
 * at Stage 3) and the bbo / book groups replaced per scenario; the {@link QualityAssessment} is
 * produced by the real Stage 3 resolver.
 */
final class BookFixtures {

    static final Instant EVENT_TIME = SignalRuleTestSupport.EVENT_TIME;
    static final Instant COMPUTED_AT = SignalRuleTestSupport.COMPUTED_AT;
    static final Instant ASSESSED_AT = EVENT_TIME.plusMillis(100);

    static final QualityEligibilityPolicy QUALITY_POLICY =
            QualityEligibilityPolicy.of(Duration.ofMillis(2_000), Duration.ofMillis(1_000), true);
    static final QualityAssessmentResolver QUALITY_RESOLVER = new QualityAssessmentResolver();

    static final String POLICY_VERSION = "book-fixture-v1";

    /** minLevels 5, top5 ±0.30, microprice ±2 bps, full strength 10 bps, safe up to 50 bps. */
    static final BookAssessmentPolicy POLICY = new BookAssessmentPolicy(POLICY_VERSION, 5,
            bd("0.30"), bd("-0.30"), bd("2"), bd("-2"), bd("10"), bd("50"));

    private BookFixtures() {
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------ features

    /** A fully computed, active trade-flow window so Stage 3 marks every horizon ELIGIBLE. */
    static TradeFlowFeature activeTradeFlow() {
        TradeFlowWindow window = TradeFlowWindow.builder()
                .signedFlowImbalance(bd("0.00"))
                .tradeCount(200)
                .validQtyTradeCount(200)
                .aggressiveTradeCount(150)
                .unknownSideCount(0)
                .tradeIntensity(bd("3.0"))
                .build();
        return TradeFlowFeature.builder()
                .window1s(window).window5s(window).window15s(window).window60s(window)
                .build();
    }

    /** A structurally valid BBO around mid 100.01 with the given microprice offset ({@code null} = absent). */
    static BboFeature bbo(String micropriceOffsetBps) {
        return BboFeature.builder()
                .bestBidPrice(bd("100.00"))
                .bestAskPrice(bd("100.02"))
                .bestBidQty(bd("5"))
                .bestAskQty(bd("5"))
                .spreadAbs(bd("0.02"))
                .spreadBps(bd("2"))
                .midPrice(bd("100.01"))
                .micropriceTop1(bd("100.011"))
                .micropriceOffsetBps(micropriceOffsetBps == null ? null : bd(micropriceOffsetBps))
                .build();
    }

    /** A book feature with enough depth for the fixture policy ({@code levelsUsed = 5}). */
    static BookFeature book(String top5Imbalance) {
        return book(5, top5Imbalance, null);
    }

    static BookFeature book(int levelsUsed, String top5Imbalance, String top1Imbalance) {
        return BookFeature.builder()
                .levelsUsed(levelsUsed)
                .top5Imbalance(top5Imbalance == null ? null : bd(top5Imbalance))
                .top1Imbalance(top1Imbalance == null ? null : bd(top1Imbalance))
                .build();
    }

    // ------------------------------------------------------------------ snapshots

    static MarketFeaturesSnapshot snapshot(BboFeature bbo, BookFeature book) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .bbo(bbo)
                .book(book)
                .build();
    }

    // ------------------------------------------------------------------ quality

    /** The real Stage 3 assessment of {@code snapshot} at a fresh {@link #ASSESSED_AT}. */
    static QualityAssessment quality(MarketFeaturesSnapshot snapshot) {
        return QUALITY_RESOLVER.resolve(snapshot, ASSESSED_AT, QUALITY_POLICY);
    }
}
