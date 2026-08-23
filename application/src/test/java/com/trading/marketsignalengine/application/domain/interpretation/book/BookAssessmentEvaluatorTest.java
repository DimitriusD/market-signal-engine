package com.trading.marketsignalengine.application.domain.interpretation.book;

import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.ASSESSED_AT;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.QUALITY_POLICY;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.QUALITY_RESOLVER;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.activeTradeFlow;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.bbo;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.bd;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.book;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.quality;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookFixtures.snapshot;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_BBO_CALCULATOR_FAILED;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_BBO_INVALID;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_BEARISH;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_BULLISH;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_INCOMPLETE;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_INDICATORS_CONFLICT;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_INDICATORS_MISSING;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_INSUFFICIENT_DEPTH;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_LEVELS_INVALID;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_MICROPRICE_OFFSET_OUT_OF_SAFE_RANGE;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_NEUTRAL;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_NOT_IN_SYNC;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_NOT_SCOPED_TO_HORIZON;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_ORDER_BOOK_CALCULATOR_FAILED;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_PARTIAL_EVIDENCE;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_SOURCE_UNTRUSTED;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_STALE;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_TOP1_IMBALANCE_OUT_OF_RANGE;
import static com.trading.marketsignalengine.application.domain.interpretation.book.BookReasonCodes.BOOK_TOP5_IMBALANCE_OUT_OF_RANGE;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H15S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H1S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H5S;
import static com.trading.marketsignalengine.application.domain.model.MarketHorizon.H60S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAvailabilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibilityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityReasonCodes;
import com.trading.marketsignalengine.application.domain.interpretation.quality.SnapshotQualityConsistencyGuard;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.domain.rule.SignalRuleTestSupport;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Book V1 evaluation: instantaneous 1S-only semantics (never copied to longer horizons), eligibility
 * precedence over BOOK_NOT_SCOPED_TO_HORIZON, failed dependencies before missing features,
 * book-specific quality gates (and only those), BBO geometry validation, the two-indicator
 * combination table, partial evidence, depth handling, determinism and immutability.
 */
class BookAssessmentEvaluatorTest {

    private final BookAssessmentEvaluator evaluator = new BookAssessmentEvaluator();

    private EvidenceAssessment evaluateH1s(MarketFeaturesSnapshot snapshot) {
        return evaluator.evaluate(snapshot, quality(snapshot), POLICY, H1S);
    }

    // ------------------------------------------------------------------ shape

    @Test
    void returnsExactlyFourBookAssessmentsInCanonicalOrder() {
        MarketFeaturesSnapshot snapshot = snapshot(bbo("6"), book("0.60"));

        BookAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(List.of(H1S, H5S, H15S, H60S), List.copyOf(assessments.asMap().keySet()));
        assertEquals(4, assessments.asList().size());
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            EvidenceAssessment evidence = assessments.of(horizon);
            assertEquals(EvidenceDimension.BOOK, evidence.dimension());
            assertEquals(evidence, evaluator.evaluate(snapshot, quality(snapshot), POLICY, horizon),
                    "per-horizon entry point agrees with the aggregate");
        }
    }

    @Test
    void rejectsNullInputs() {
        MarketFeaturesSnapshot snapshot = snapshot(bbo("6"), book("0.60"));
        QualityAssessment qa = quality(snapshot);

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null, qa, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, null, POLICY));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, null));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(snapshot, qa, POLICY, null));
    }

    @Test
    void consistencyGuardRunsExactlyOncePerPublicEvaluation() {
        AtomicInteger verifications = new AtomicInteger();
        BookAssessmentEvaluator counted = new BookAssessmentEvaluator(new SnapshotQualityConsistencyGuard() {
            @Override
            public void verify(MarketFeaturesSnapshot snapshot, QualityAssessment qualityAssessment) {
                verifications.incrementAndGet();
                super.verify(snapshot, qualityAssessment);
            }
        });
        MarketFeaturesSnapshot snapshot = snapshot(bbo("6"), book("0.60"));
        QualityAssessment qa = quality(snapshot);

        counted.evaluate(snapshot, qa, POLICY);
        assertEquals(1, verifications.get(), "aggregate evaluation verifies once, not once per horizon");
        counted.evaluate(snapshot, qa, POLICY, H1S);
        assertEquals(2, verifications.get(), "per-horizon evaluation verifies once");
    }

    @Test
    void qualityAssessmentOfAnotherSnapshotIsRejected() {
        MarketFeaturesSnapshot snapshot = snapshot(bbo("6"), book("0.60"));
        QualityAssessment qa = quality(snapshot);
        MarketFeaturesSnapshot otherAsOf = snapshot.toBuilder()
                .evaluationTs(BookFixtures.EVENT_TIME.plusMillis(7)).build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(otherAsOf, qa, POLICY));
        assertTrue(ex.getMessage().contains("not produced from this snapshot"), ex.getMessage());
    }

    // ------------------------------------------------------------------ horizon scoping

    @Test
    void longerHorizonsAreExplicitlyUnavailableAndNeverCopiesOfThe1sReading() {
        MarketFeaturesSnapshot snapshot = snapshot(bbo("6"), book("0.60"));
        BookAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(InterpretationDirection.BULLISH, assessments.of(H1S).direction());
        for (MarketHorizon horizon : List.of(H5S, H15S, H60S)) {
            EvidenceAssessment evidence = assessments.of(horizon);
            assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(InterpretationDirection.UNKNOWN, evidence.direction(),
                    "the 1S bullish reading is never copied to " + horizon.wireValue());
            assertNull(evidence.evidenceStrength());
            assertEquals(List.of(BOOK_NOT_SCOPED_TO_HORIZON), evidence.reasonCodes());
        }
    }

    @Test
    void eligibilityPrecedenceWinsOverNotScopedOnLongerHorizons() {
        // history gap: 1S/5S computed and ELIGIBLE, uncovered 15S/60S UNTRUSTED at Stage 3
        MarketFeaturesSnapshot gap = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow().toBuilder().window15s(null).window60s(null).build())
                .bbo(bbo("6"))
                .book(book("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("TRADE_HISTORY_GAP")).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(gap, ASSESSED_AT, QUALITY_POLICY);

        BookAssessments assessments = evaluator.evaluate(gap, qa, POLICY);

        assertEquals(InterpretationDirection.BULLISH, assessments.of(H1S).direction());
        assertEquals(List.of(BOOK_NOT_SCOPED_TO_HORIZON), assessments.of(H5S).reasonCodes(),
                "an eligible 5S is 'not scoped'");
        for (MarketHorizon horizon : List.of(H15S, H60S)) {
            EvidenceAssessment evidence = assessments.of(horizon);
            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, evidence.availabilityStatus(), horizon.wireValue());
            assertEquals(List.of(QualityReasonCodes.TRADE_HISTORY_GAP), evidence.reasonCodes(),
                    "a non-eligible horizon keeps the projected eligibility reasons, not NOT_SCOPED");
        }
    }

    // ------------------------------------------------------------------ combination table

    @Test
    void confirmedBullishTakesTheWeakerStrength() {
        // top5 0.60 (strength 0.6), offset 6 (strength 0.6) — and an asymmetric pair
        EvidenceAssessment confirmed = evaluateH1s(snapshot(bbo("6"), book("0.60")));
        assertEquals(InterpretationDirection.BULLISH, confirmed.direction());
        assertEquals(EvidenceStrength.of("0.6"), confirmed.evidenceStrength());
        assertEquals(List.of(BOOK_BULLISH), confirmed.reasonCodes());

        EvidenceAssessment weakerTop5 = evaluateH1s(snapshot(bbo("9"), book("0.40")));
        assertEquals(InterpretationDirection.BULLISH, weakerTop5.direction());
        assertEquals(EvidenceStrength.of("0.4"), weakerTop5.evidenceStrength(), "min(0.4, 0.9)");
    }

    @Test
    void confirmedBearishTakesTheWeakerStrength() {
        EvidenceAssessment confirmed = evaluateH1s(snapshot(bbo("-8"), book("-0.50")));

        assertEquals(InterpretationDirection.BEARISH, confirmed.direction());
        assertEquals(EvidenceStrength.of("0.5"), confirmed.evidenceStrength(), "min(0.5, 0.8)");
        assertEquals(List.of(BOOK_BEARISH), confirmed.reasonCodes());
    }

    @Test
    void directionalPlusNeutralUsesTheDirectionalIndicator() {
        // bullish top5 + neutral microprice
        EvidenceAssessment bullishTop5 = evaluateH1s(snapshot(bbo("0.5"), book("0.60")));
        assertEquals(InterpretationDirection.BULLISH, bullishTop5.direction());
        assertEquals(EvidenceStrength.of("0.6"), bullishTop5.evidenceStrength());
        assertEquals(List.of(BOOK_BULLISH), bullishTop5.reasonCodes());

        // neutral top5 + bearish microprice
        EvidenceAssessment bearishMicro = evaluateH1s(snapshot(bbo("-8"), book("0.10")));
        assertEquals(InterpretationDirection.BEARISH, bearishMicro.direction());
        assertEquals(EvidenceStrength.of("0.8"), bearishMicro.evidenceStrength(), "8 / 10");
        assertEquals(List.of(BOOK_BEARISH), bearishMicro.reasonCodes());
    }

    @Test
    void neutralPlusNeutralIsNeutralWithZeroStrength() {
        EvidenceAssessment neutral = evaluateH1s(snapshot(bbo("1"), book("0.10")));

        assertEquals(InterpretationDirection.NEUTRAL, neutral.direction());
        assertEquals(EvidenceStrength.MIN, neutral.evidenceStrength(), "neutral strength is a real 0");
        assertEquals(List.of(BOOK_NEUTRAL), neutral.reasonCodes());
    }

    @Test
    void conflictingIndicatorsAreMixedWithNoStrength() {
        for (MarketFeaturesSnapshot snapshot : List.of(
                snapshot(bbo("-8"), book("0.60")),   // bullish top5 vs bearish microprice
                snapshot(bbo("6"), book("-0.50")))) { // bearish top5 vs bullish microprice
            EvidenceAssessment mixed = evaluateH1s(snapshot);

            assertEquals(EvidenceAvailabilityStatus.AVAILABLE, mixed.availabilityStatus());
            assertEquals(InterpretationDirection.MIXED, mixed.direction());
            assertNull(mixed.evidenceStrength(), "a conflict has no single strength");
            assertEquals(List.of(BOOK_INDICATORS_CONFLICT), mixed.reasonCodes());
        }
    }

    @ParameterizedTest
    @CsvSource({
            // top5 boundaries (inclusive on the directional side)
            "0.30, BULLISH", "0.299999, NEUTRAL", "-0.30, BEARISH", "-0.299999, NEUTRAL",
    })
    void top5ThresholdsAreInclusive(String top5, InterpretationDirection expected) {
        // microprice neutral so the top5 indicator decides
        EvidenceAssessment evidence = evaluateH1s(snapshot(bbo("0"), book(top5)));
        assertEquals(expected, evidence.direction(), top5);
    }

    @ParameterizedTest
    @CsvSource({
            "2, BULLISH", "1.999999, NEUTRAL", "-2, BEARISH", "-1.999999, NEUTRAL",
    })
    void micropriceThresholdsAreInclusive(String offset, InterpretationDirection expected) {
        // top5 neutral so the microprice indicator decides
        EvidenceAssessment evidence = evaluateH1s(snapshot(bbo(offset), book("0.10")));
        assertEquals(expected, evidence.direction(), offset);
    }

    // ------------------------------------------------------------------ partial / missing indicators

    @Test
    void top5OnlyIsPartialEvidence() {
        // the BBO is valid but carries no microprice offset
        EvidenceAssessment partial = evaluateH1s(snapshot(bbo(null), book("0.60")));

        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, partial.availabilityStatus());
        assertEquals(InterpretationDirection.BULLISH, partial.direction());
        assertEquals(EvidenceStrength.of("0.6"), partial.evidenceStrength());
        assertEquals(List.of(BOOK_PARTIAL_EVIDENCE, BOOK_BULLISH), partial.reasonCodes());

        // absent bbo group entirely: same partial semantics
        EvidenceAssessment noBbo = evaluateH1s(snapshot(null, book("-0.50")));
        assertEquals(InterpretationDirection.BEARISH, noBbo.direction());
        assertEquals(List.of(BOOK_PARTIAL_EVIDENCE, BOOK_BEARISH), noBbo.reasonCodes());
    }

    @Test
    void micropriceOnlyIsPartialEvidence() {
        // book group absent
        EvidenceAssessment noBook = evaluateH1s(snapshot(bbo("-8"), null));
        assertEquals(InterpretationDirection.BEARISH, noBook.direction());
        assertEquals(EvidenceStrength.of("0.8"), noBook.evidenceStrength());
        assertEquals(List.of(BOOK_PARTIAL_EVIDENCE, BOOK_BEARISH), noBook.reasonCodes());

        // book present but top5 value absent
        EvidenceAssessment noTop5 = evaluateH1s(snapshot(bbo("6"), book(null)));
        assertEquals(InterpretationDirection.BULLISH, noTop5.direction());
        assertEquals(List.of(BOOK_PARTIAL_EVIDENCE, BOOK_BULLISH), noTop5.reasonCodes());

        // a single neutral indicator still reads a real 0 strength
        EvidenceAssessment neutral = evaluateH1s(snapshot(bbo("0.5"), null));
        assertEquals(InterpretationDirection.NEUTRAL, neutral.direction());
        assertEquals(EvidenceStrength.MIN, neutral.evidenceStrength());
        assertEquals(List.of(BOOK_PARTIAL_EVIDENCE, BOOK_NEUTRAL), neutral.reasonCodes());
    }

    @Test
    void bothIndicatorsMissingIsUnavailableNotNeutral() {
        for (MarketFeaturesSnapshot snapshot : List.of(
                snapshot(null, null),
                snapshot(bbo(null), book(null)))) {
            EvidenceAssessment missing = evaluateH1s(snapshot);

            assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, missing.availabilityStatus());
            assertEquals(InterpretationDirection.UNKNOWN, missing.direction(), "a missing indicator is not neutral");
            assertNull(missing.evidenceStrength());
            assertEquals(List.of(BOOK_INDICATORS_MISSING), missing.reasonCodes());
        }
    }

    // ------------------------------------------------------------------ depth

    @Test
    void nonPositiveLevelsUsedIsUntrusted() {
        for (int levelsUsed : List.of(0, -3)) {
            EvidenceAssessment untrusted = evaluateH1s(snapshot(bbo("6"), book(levelsUsed, "0.60", null)));

            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, untrusted.availabilityStatus(), "levelsUsed " + levelsUsed);
            assertEquals(InterpretationDirection.UNKNOWN, untrusted.direction());
            assertEquals(List.of(BOOK_LEVELS_INVALID), untrusted.reasonCodes());
        }
    }

    @Test
    void insufficientDepthWithUsableMicropriceIsPartialEvidence() {
        // 3 < minimumLevelsUsed 5: the strongly bullish top5 must not vote; the bearish microprice may
        EvidenceAssessment partial = evaluateH1s(snapshot(bbo("-8"), book(3, "0.90", null)));

        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, partial.availabilityStatus());
        assertEquals(InterpretationDirection.BEARISH, partial.direction(), "top5 is dropped, not averaged in");
        assertEquals(EvidenceStrength.of("0.8"), partial.evidenceStrength());
        assertEquals(List.of(BOOK_INSUFFICIENT_DEPTH, BOOK_PARTIAL_EVIDENCE, BOOK_BEARISH), partial.reasonCodes());
    }

    @Test
    void insufficientDepthWithoutMicropriceIsUnavailable() {
        EvidenceAssessment unavailable = evaluateH1s(snapshot(bbo(null), book(3, "0.90", null)));

        assertEquals(EvidenceAvailabilityStatus.UNAVAILABLE, unavailable.availabilityStatus());
        assertEquals(InterpretationDirection.UNKNOWN, unavailable.direction());
        assertEquals(List.of(BOOK_INSUFFICIENT_DEPTH, BOOK_INDICATORS_MISSING), unavailable.reasonCodes());
    }

    // ------------------------------------------------------------------ invalid indicator values

    @Test
    void outOfRangeImbalancesAreUntrusted() {
        EvidenceAssessment badTop5 = evaluateH1s(snapshot(bbo("6"), book(5, "1.000001", null)));
        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, badTop5.availabilityStatus());
        assertEquals(List.of(BOOK_TOP5_IMBALANCE_OUT_OF_RANGE), badTop5.reasonCodes());

        // top1 is not a vote yet, but a corrupt value still discredits the book feature
        EvidenceAssessment badTop1 = evaluateH1s(snapshot(bbo("6"), book(5, "0.60", "-1.5")));
        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, badTop1.availabilityStatus());
        assertEquals(List.of(BOOK_TOP1_IMBALANCE_OUT_OF_RANGE), badTop1.reasonCodes());

        // exact ±1 are valid
        EvidenceAssessment atBounds = evaluateH1s(snapshot(bbo("6"), book(5, "1", "-1")));
        assertEquals(InterpretationDirection.BULLISH, atBounds.direction());
        assertEquals(EvidenceStrength.of("0.6"), atBounds.evidenceStrength(), "min(1, 0.6)");
    }

    @Test
    void micropriceOffsetMaxSafeBoundaryIsAcceptedAndAboveIsUntrusted() {
        // top5 missing so the microprice indicator is isolated; maxSafe 50, full strength 10
        EvidenceAssessment atMax = evaluateH1s(snapshot(bbo("50"), null));
        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, atMax.availabilityStatus());
        assertEquals(InterpretationDirection.BULLISH, atMax.direction());
        assertEquals(EvidenceStrength.MAX, atMax.evidenceStrength(), "saturated at full strength 10");

        for (String offset : List.of("50.000001", "-50.000001", "1000")) {
            EvidenceAssessment aboveMax = evaluateH1s(snapshot(bbo(offset), null));
            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, aboveMax.availabilityStatus(), offset);
            assertEquals(InterpretationDirection.UNKNOWN, aboveMax.direction());
            assertEquals(List.of(BOOK_MICROPRICE_OFFSET_OUT_OF_SAFE_RANGE), aboveMax.reasonCodes());
        }
    }

    @Test
    void strengthSaturatesAtFullStrengthOffset() {
        assertEquals(EvidenceStrength.of("0.35"),
                evaluateH1s(snapshot(bbo("3.5"), null)).evidenceStrength(), "3.5 / 10");
        assertEquals(EvidenceStrength.MAX, evaluateH1s(snapshot(bbo("10"), null)).evidenceStrength(),
                "exact full-strength boundary");
        assertEquals(EvidenceStrength.MAX, evaluateH1s(snapshot(bbo("35"), null)).evidenceStrength(),
                "capped at 1 within the safe range");
    }

    // ------------------------------------------------------------------ BBO geometry

    @Test
    void invalidBboGeometryIsUntrusted() {
        List<BboFeature> invalid = List.of(
                bbo("6").toBuilder().bestBidPrice(bd("0")).build(),
                bbo("6").toBuilder().bestAskPrice(bd("-1")).build(),
                // crossed: bid above ask
                bbo("6").toBuilder().bestBidPrice(bd("100.05")).micropriceTop1(null).build(),
                bbo("6").toBuilder().spreadAbs(bd("-0.01")).build(),
                bbo("6").toBuilder().spreadBps(bd("-1")).build(),
                bbo("6").toBuilder().bestBidQty(bd("-1")).build(),
                bbo("6").toBuilder().bestAskQty(bd("-1")).build(),
                bbo("6").toBuilder().midPrice(bd("0")).build(),
                bbo("6").toBuilder().micropriceTop1(bd("-100")).build(),
                // microprice outside [bid, ask]
                bbo("6").toBuilder().micropriceTop1(bd("100.50")).build(),
                bbo("6").toBuilder().micropriceTop1(bd("99.50")).build());

        for (BboFeature bad : invalid) {
            EvidenceAssessment untrusted = evaluateH1s(snapshot(bad, book("0.60")));
            assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, untrusted.availabilityStatus(), bad.toString());
            assertEquals(InterpretationDirection.UNKNOWN, untrusted.direction(), "no direction from corrupt geometry");
            assertEquals(List.of(BOOK_BBO_INVALID), untrusted.reasonCodes());
        }
    }

    @Test
    void wideButValidSpreadDoesNotChangeTheDirection() {
        // 500 bps wide, structurally consistent: not invalid, not bearish, not a vote
        BboFeature wide = BboFeature.builder()
                .bestBidPrice(bd("100.00")).bestAskPrice(bd("105.00"))
                .bestBidQty(bd("5")).bestAskQty(bd("5"))
                .spreadAbs(bd("5.00")).spreadBps(bd("500"))
                .midPrice(bd("102.50")).micropriceTop1(bd("102.60"))
                .micropriceOffsetBps(bd("6"))
                .build();

        EvidenceAssessment evidence = evaluateH1s(snapshot(wide, book("0.60")));

        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, evidence.availabilityStatus());
        assertEquals(InterpretationDirection.BULLISH, evidence.direction());
        assertEquals(List.of(BOOK_BULLISH), evidence.reasonCodes(), "spread magnitude adds no code");
    }

    // ------------------------------------------------------------------ failed dependencies

    @Test
    void failedBboOrOrderBookGroupIsFailedBeforeMissingFeatures() {
        // the feature objects are even absent — the failed dependency is still reported first
        EvidenceAssessment failedBbo = evaluateH1s(withFailedGroups(List.of("bbo")));
        assertEquals(EvidenceAvailabilityStatus.FAILED, failedBbo.availabilityStatus());
        assertEquals(List.of(BOOK_BBO_CALCULATOR_FAILED), failedBbo.reasonCodes());

        EvidenceAssessment failedOrderBook = evaluateH1s(withFailedGroups(List.of("order-book")));
        assertEquals(EvidenceAvailabilityStatus.FAILED, failedOrderBook.availabilityStatus());
        assertEquals(List.of(BOOK_ORDER_BOOK_CALCULATOR_FAILED), failedOrderBook.reasonCodes());

        EvidenceAssessment bothFailed = evaluateH1s(withFailedGroups(List.of("bbo", "order-book")));
        assertEquals(EvidenceAvailabilityStatus.FAILED, bothFailed.availabilityStatus());
        assertEquals(List.of(BOOK_BBO_CALCULATOR_FAILED, BOOK_ORDER_BOOK_CALCULATOR_FAILED), bothFailed.reasonCodes());
    }

    private static MarketFeaturesSnapshot withFailedGroups(List<String> groups) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .bbo(null)
                .book(null)
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("CALCULATOR_FAILURE")).build())
                .diagnostics(FeatureDiagnostics.builder().failedFeatureGroups(groups).totalFeatureGroups(4).build())
                .build();
    }

    @Test
    void failedShortTermRegimeGroupIsNotABookDependency() {
        // a failed short-term-regime group leaves book evidence untouched (eligibility also unaffected)
        MarketFeaturesSnapshot failedRegime = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .bbo(bbo("6"))
                .book(book("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("CALCULATOR_FAILURE")).build())
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("short-term-regime")).totalFeatureGroups(4).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(failedRegime, ASSESSED_AT, QUALITY_POLICY);

        EvidenceAssessment evidence = evaluator.evaluate(failedRegime, qa, POLICY, H1S);

        assertEquals(EvidenceAvailabilityStatus.AVAILABLE, evidence.availabilityStatus());
        assertEquals(InterpretationDirection.BULLISH, evidence.direction());
        assertEquals(List.of(BOOK_BULLISH), evidence.reasonCodes());
    }

    @Test
    void failedTradeFlowGroupProjectsFailedThroughEligibilityPrecedence() {
        // trade-flow is not a book dependency, but Stage 3 eligibility is trade-flow-backed: a failed
        // trade-flow group fails every horizon, so H1S book evidence is the eligibility projection —
        // the strongly bullish book values are never read and no BOOK_* code is added
        MarketFeaturesSnapshot failedTradeFlow = SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .bbo(bbo("6"))
                .book(book("0.60"))
                .quality(SignalRuleTestSupport.tradableQuality().toBuilder()
                        .status(FeatureQualityStatus.DEGRADED)
                        .qualityReasons(List.of("CALCULATOR_FAILURE")).build())
                .diagnostics(FeatureDiagnostics.builder()
                        .failedFeatureGroups(List.of("trade-flow")).totalFeatureGroups(4).build())
                .build();
        QualityAssessment qa = QUALITY_RESOLVER.resolve(failedTradeFlow, ASSESSED_AT, QUALITY_POLICY);
        assertEquals(HorizonEligibilityStatus.FAILED, qa.horizonEligibilities().statusOf(H1S),
                "precondition: a failed trade-flow group fails the horizon at Stage 3");

        EvidenceAssessment h1s = evaluator.evaluate(failedTradeFlow, qa, POLICY, H1S);

        assertEquals(EvidenceAvailabilityStatus.FAILED, h1s.availabilityStatus(), "projected from eligibility");
        assertEquals(InterpretationDirection.UNKNOWN, h1s.direction());
        assertNull(h1s.evidenceStrength());
        assertEquals(List.of(QualityReasonCodes.TRADE_FLOW_CALCULATOR_FAILED), h1s.reasonCodes(),
                "the eligibility reason is kept verbatim");
        assertFalse(h1s.reasonCodes().stream().anyMatch(code -> code.value().startsWith("BOOK_")),
                "no book-specific code is added on top of the projection");
    }

    // ------------------------------------------------------------------ book-specific quality

    @Test
    void bookSpecificQualityFaultsAreUntrustedWithAllApplicableCodes() {
        // untrusted source book (UNSAFE upstream, trade-flow horizons still eligible)
        EvidenceAssessment sourceUntrusted = evaluateH1s(withQuality(q -> q
                .status(FeatureQualityStatus.UNSAFE)
                .sourceOrderBookTrusted(false)
                .sourceOrderBookReason("BOOK_UNTRUSTED")
                .qualityReasons(List.of("BOOK_UNTRUSTED"))));
        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, sourceUntrusted.availabilityStatus());
        assertEquals(List.of(BOOK_SOURCE_UNTRUSTED), sourceUntrusted.reasonCodes());

        EvidenceAssessment notInSync = evaluateH1s(withQuality(q -> q
                .status(FeatureQualityStatus.DEGRADED)
                .syncStatus(SyncStatus.RECOVERING)
                .qualityReasons(List.of("OUT_OF_SYNC"))));
        assertEquals(List.of(BOOK_NOT_IN_SYNC), notInSync.reasonCodes());

        EvidenceAssessment stale = evaluateH1s(withQuality(q -> q
                .status(FeatureQualityStatus.DEGRADED)
                .staleOrderBookState(true)
                .qualityReasons(List.of("STALE_BOOK"))));
        assertEquals(List.of(BOOK_STALE), stale.reasonCodes());

        EvidenceAssessment incomplete = evaluateH1s(withQuality(q -> q
                .status(FeatureQualityStatus.DEGRADED)
                .incompleteBook(true)
                .qualityReasons(List.of("INCOMPLETE_BOOK"))));
        assertEquals(List.of(BOOK_INCOMPLETE), incomplete.reasonCodes());

        EvidenceAssessment several = evaluateH1s(withQuality(q -> q
                .status(FeatureQualityStatus.DEGRADED)
                .syncStatus(SyncStatus.STALE)
                .staleOrderBookState(true)
                .incompleteBook(true)
                .qualityReasons(List.of("STALE_BOOK"))));
        assertEquals(List.of(BOOK_NOT_IN_SYNC, BOOK_STALE, BOOK_INCOMPLETE), several.reasonCodes(),
                "all applicable book faults are reported together in deterministic order");
    }

    private interface QualityCustomizer {
        FeatureQuality.FeatureQualityBuilder apply(FeatureQuality.FeatureQualityBuilder builder);
    }

    private static MarketFeaturesSnapshot withQuality(QualityCustomizer customizer) {
        return SignalRuleTestSupport.tradableFeaturesBuilder()
                .tradeFlow(activeTradeFlow())
                .bbo(bbo("6"))
                .book(book("0.60"))
                .quality(customizer.apply(SignalRuleTestSupport.tradableQuality().toBuilder()).build())
                .build();
    }

    @Test
    void staleTradesAloneIsNotABookSpecificFault() {
        // stale trades project every horizon at Stage 3 (trade-flow-backed eligibility) — the book
        // evaluator adds no BOOK_* code on top and never reads the strongly bullish book values
        MarketFeaturesSnapshot staleTrades = withQuality(q -> q
                .status(FeatureQualityStatus.DEGRADED)
                .staleTrades(true)
                .qualityReasons(List.of("STALE_TRADES")));
        QualityAssessment qa = QUALITY_RESOLVER.resolve(staleTrades, ASSESSED_AT, QUALITY_POLICY);

        EvidenceAssessment h1s = evaluator.evaluate(staleTrades, qa, POLICY, H1S);

        assertEquals(EvidenceAvailabilityStatus.UNTRUSTED, h1s.availabilityStatus(), "projected from eligibility");
        assertEquals(List.of(QualityReasonCodes.STALE_TRADES), h1s.reasonCodes(),
                "the eligibility reason is kept verbatim; no book-specific code is added");
        assertFalse(h1s.reasonCodes().stream().anyMatch(code -> code.value().startsWith("BOOK_")));

        // unit level: stale trades and non-book degradation are not book quality faults
        assertTrue(BookAssessmentEvaluator.bookQualityFaults(
                SignalRuleTestSupport.tradableQuality().toBuilder().staleTrades(true).build()).isEmpty());
    }

    // ------------------------------------------------------------------ determinism / immutability

    @Test
    void sameInputAndPolicyGiveValueEqualResults() {
        MarketFeaturesSnapshot snapshot = snapshot(bbo("6"), book("0.60"));
        QualityAssessment qa = quality(snapshot);

        BookAssessments first = evaluator.evaluate(snapshot, qa, POLICY);
        BookAssessments second = new BookAssessmentEvaluator().evaluate(snapshot, quality(snapshot), POLICY);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString());
    }

    @Test
    void resultCollectionsAreImmutable() {
        MarketFeaturesSnapshot snapshot = snapshot(bbo("6"), book("0.60"));
        BookAssessments assessments = evaluator.evaluate(snapshot, quality(snapshot), POLICY);

        assertThrows(UnsupportedOperationException.class, () -> assessments.asMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> assessments.asList().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> assessments.of(H1S).reasonCodes().add(BOOK_NEUTRAL));
    }
}
