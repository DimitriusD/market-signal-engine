package com.trading.marketsignalengine.application.domain.interpretation.book;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceDimension;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceEligibilityProjection;
import com.trading.marketsignalengine.application.domain.interpretation.EvidenceStrength;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.InterpretationDirection;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.quality.FeatureGroupId;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.SnapshotQualityConsistencyGuard;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.BboFeature;
import com.trading.marketsignalengine.application.domain.model.feature.BookFeature;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, deterministic Book V1 evaluator: one {@code BOOK} {@link EvidenceAssessment} per
 * {@link MarketHorizon} from a validated {@link MarketFeaturesSnapshot}, the Stage 3
 * {@link QualityAssessment} and an explicit {@link BookAssessmentPolicy}. No Spring, Kafka, Avro,
 * infrastructure, {@code Clock}, {@code Instant.now()} or metrics; same input + policy ⇒ equal
 * result.
 *
 * <h2>Horizon semantics</h2>
 * {@code BboFeature} / {@code BookFeature} describe one <em>instantaneous</em> book snapshot — MFS v2
 * publishes no rolling-window book features — so only 1S carries real book evidence. An eligible
 * 5S/15S/60S horizon is UNAVAILABLE with {@code BOOK_NOT_SCOPED_TO_HORIZON}; the 1S reading is never
 * copied onto longer horizons. Eligibility precedence still comes first: a non-ELIGIBLE 15S keeps its
 * projected status and eligibility reasons, and {@code BOOK_NOT_SCOPED_TO_HORIZON} applies only to an
 * eligible one.
 *
 * <h2>1S pipeline (first match wins)</h2>
 * <ol>
 *   <li><b>Eligibility precedence</b> via the shared {@link EvidenceEligibilityProjection}. The
 *       Stage 3 eligibility is trade-flow-backed, so e.g. stale <em>trades</em> project all horizons
 *       — such a projection keeps the eligibility reasons verbatim and no {@code BOOK_*} code is
 *       added (stale trades are not a book-specific fault).</li>
 *   <li><b>Failed dependency → FAILED</b>, checked before any feature object is read: a failed
 *       {@code bbo} group ({@code BOOK_BBO_CALCULATOR_FAILED}), a failed {@code order-book} group
 *       ({@code BOOK_ORDER_BOOK_CALCULATOR_FAILED}), both codes when both failed. A failed
 *       {@code short-term-regime} group is not a book dependency and leaves book evidence untouched.
 *       A failed {@code trade-flow} group is not a book-specific fault either, but it fails every
 *       horizon at Stage 3 (eligibility is trade-flow-backed), so book evidence is then the
 *       eligibility <em>projection</em> — FAILED with the eligibility reasons verbatim, no
 *       {@code BOOK_*} code — via step 1, never via this step.</li>
 *   <li><b>Book-specific quality → UNTRUSTED.</b> {@code sourceOrderBookTrusted == false}
 *       ({@code BOOK_SOURCE_UNTRUSTED}), {@code syncStatus != IN_SYNC} ({@code BOOK_NOT_IN_SYNC}),
 *       {@code staleOrderBookState} ({@code BOOK_STALE}), {@code incompleteBook}
 *       ({@code BOOK_INCOMPLETE}) — all applicable codes reported together. {@code staleTrades} and
 *       an aggregate DEGRADED whose degradation does not concern the book are deliberately not
 *       book-specific blocks.</li>
 *   <li><b>Invalid geometry / indicators → UNTRUSTED.</b> Structurally impossible BBO geometry
 *       (see {@link #bboGeometryInvalid}: non-positive or crossed prices, negative spread or
 *       quantity, non-positive mid/microprice, microprice outside {@code [bid, ask]}) —
 *       {@code BOOK_BBO_INVALID}; {@code levelsUsed <= 0} ({@code BOOK_LEVELS_INVALID});
 *       {@code top1Imbalance} / {@code top5Imbalance} outside {@code [-1, 1]}
 *       ({@code BOOK_TOP1/TOP5_IMBALANCE_OUT_OF_RANGE});
 *       {@code abs(micropriceOffsetBps) > maxSafeAbsMicropriceOffsetBps}
 *       ({@code BOOK_MICROPRICE_OFFSET_OUT_OF_SAFE_RANGE}). All applicable codes reported together;
 *       no direction is ever derived from corrupt values. A wide but structurally valid spread is
 *       <em>not</em> invalid and never changes the direction.</li>
 *   <li><b>Indicator availability.</b> The two indicators are {@code BookFeature.top5Imbalance}
 *       (usable only when {@code levelsUsed >= minimumLevelsUsed}) and
 *       {@code BboFeature.micropriceOffsetBps}. {@code 0 < levelsUsed < minimumLevelsUsed} adds
 *       {@code BOOK_INSUFFICIENT_DEPTH} and drops the top-5 indicator (never reads it as a vote).
 *       Neither indicator usable → UNAVAILABLE with {@code BOOK_INDICATORS_MISSING} — a missing
 *       indicator is never a zero or neutral reading.</li>
 *   <li><b>Direction and strength.</b> Per indicator, inclusive on the directional side:
 *       {@code top5 >= bullishTop5ImbalanceThreshold → BULLISH}, {@code <= bearish → BEARISH}, else
 *       NEUTRAL, strength {@code |top5Imbalance|}; {@code offset >= bullishMicropriceOffsetBps →
 *       BULLISH}, {@code <= bearish → BEARISH}, else NEUTRAL, strength
 *       {@code min(1, |offset| / fullStrengthAbsMicropriceOffsetBps)}. Combination:
 *       same direction → that direction with {@code min} of the two strengths; opposite directions →
 *       MIXED with no strength ({@code BOOK_INDICATORS_CONFLICT}); directional + neutral → the
 *       directional indicator with its strength; neutral + neutral → NEUTRAL with a real 0 strength.
 *       One usable indicator → its direction ({@code BOOK_PARTIAL_EVIDENCE}); a single NEUTRAL
 *       indicator also reads a real 0 strength (consistent with every NEUTRAL verdict). No invented
 *       weights, and {@code top1Imbalance} is range-checked but not a vote.</li>
 * </ol>
 *
 * <p>The shared {@link SnapshotQualityConsistencyGuard} cross-checks that the assessment was produced
 * from this snapshot and runs exactly once per public {@code evaluate(...)} call, never once per
 * horizon.
 */
public final class BookAssessmentEvaluator {

    private static final BigDecimal MINUS_ONE = BigDecimal.ONE.negate();

    /**
     * Scale and rounding of the microprice strength ratio; {@link RoundingMode#DOWN} never inflates
     * the exact ratio, so saturation happens exactly at {@code abs(offset) >= fullStrength}.
     */
    static final int STRENGTH_SCALE = 6;
    static final RoundingMode STRENGTH_ROUNDING = RoundingMode.DOWN;

    /** Shared snapshot ↔ assessment consistency check; stateless, so the evaluator stays pure and thread-safe. */
    private final SnapshotQualityConsistencyGuard consistencyGuard;

    public BookAssessmentEvaluator() {
        this(new SnapshotQualityConsistencyGuard());
    }

    /** Package-private for tests; production uses the canonical guard. */
    BookAssessmentEvaluator(SnapshotQualityConsistencyGuard consistencyGuard) {
        this.consistencyGuard = requireNonNull(consistencyGuard, "consistencyGuard");
    }

    /** BOOK evidence for all four horizons, in canonical order. The consistency guard runs once. */
    public BookAssessments evaluate(MarketFeaturesSnapshot snapshot,
                                    QualityAssessment qualityAssessment,
                                    BookAssessmentPolicy policy) {
        validateInputs(snapshot, qualityAssessment, policy);
        consistencyGuard.verify(snapshot, qualityAssessment);
        Map<MarketHorizon, EvidenceAssessment> result = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            result.put(horizon, evaluateValidated(snapshot, qualityAssessment, policy, horizon));
        }
        return new BookAssessments(result);
    }

    /** BOOK evidence for one horizon. The consistency guard runs once. */
    public EvidenceAssessment evaluate(MarketFeaturesSnapshot snapshot,
                                       QualityAssessment qualityAssessment,
                                       BookAssessmentPolicy policy,
                                       MarketHorizon horizon) {
        validateInputs(snapshot, qualityAssessment, policy);
        requireNonNull(horizon, "horizon");
        consistencyGuard.verify(snapshot, qualityAssessment);
        return evaluateValidated(snapshot, qualityAssessment, policy, horizon);
    }

    private static void validateInputs(MarketFeaturesSnapshot snapshot,
                                       QualityAssessment qualityAssessment,
                                       BookAssessmentPolicy policy) {
        requireNonNull(snapshot, "snapshot");
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(policy, "book policy");
    }

    /** One horizon after common input validation and the consistency guard; no re-validation here. */
    private static EvidenceAssessment evaluateValidated(MarketFeaturesSnapshot snapshot,
                                                        QualityAssessment qualityAssessment,
                                                        BookAssessmentPolicy policy,
                                                        MarketHorizon horizon) {
        // 1. Eligibility precedence: no feature value is read for a non-ELIGIBLE horizon.
        HorizonEligibility eligibility = qualityAssessment.eligibilityOf(horizon);
        if (!eligibility.isEligible()) {
            return EvidenceEligibilityProjection.project(EvidenceDimension.BOOK, eligibility);
        }
        List<ReasonCode> inherited = eligibility.reasonCodes();

        // 2. Only 1S carries instantaneous book evidence; the 1S reading is never copied out.
        if (horizon != MarketHorizon.H1S) {
            return EvidenceAssessment.unavailable(EvidenceDimension.BOOK,
                    concat(inherited, List.of(BookReasonCodes.BOOK_NOT_SCOPED_TO_HORIZON)));
        }

        // 3. Failed dependency → FAILED, before any feature object is read.
        List<ReasonCode> failed = new ArrayList<>(2);
        if (qualityAssessment.hasFailedFeatureGroup(FeatureGroupId.BBO)) {
            failed.add(BookReasonCodes.BOOK_BBO_CALCULATOR_FAILED);
        }
        if (qualityAssessment.hasFailedFeatureGroup(FeatureGroupId.ORDER_BOOK)) {
            failed.add(BookReasonCodes.BOOK_ORDER_BOOK_CALCULATOR_FAILED);
        }
        if (!failed.isEmpty()) {
            return EvidenceAssessment.failed(EvidenceDimension.BOOK, concat(inherited, failed));
        }

        // 4. Book-specific quality → UNTRUSTED (staleTrades and non-book degradation are not book faults).
        List<ReasonCode> qualityFaults = bookQualityFaults(snapshot.quality());
        if (!qualityFaults.isEmpty()) {
            return EvidenceAssessment.untrusted(EvidenceDimension.BOOK, concat(inherited, qualityFaults));
        }

        // 5. Invalid geometry / indicator values → UNTRUSTED (no direction from corrupt values).
        BboFeature bbo = snapshot.bbo();
        BookFeature book = snapshot.book();
        List<ReasonCode> invalid = new ArrayList<>(4);
        if (bboGeometryInvalid(bbo)) {
            invalid.add(BookReasonCodes.BOOK_BBO_INVALID);
        }
        if (book != null && book.levelsUsed() <= 0) {
            invalid.add(BookReasonCodes.BOOK_LEVELS_INVALID);
        }
        if (book != null && outsideUnitRange(book.top1Imbalance())) {
            invalid.add(BookReasonCodes.BOOK_TOP1_IMBALANCE_OUT_OF_RANGE);
        }
        if (book != null && outsideUnitRange(book.top5Imbalance())) {
            invalid.add(BookReasonCodes.BOOK_TOP5_IMBALANCE_OUT_OF_RANGE);
        }
        BigDecimal micropriceOffsetBps = bbo == null ? null : bbo.micropriceOffsetBps();
        if (micropriceOffsetBps != null
                && micropriceOffsetBps.abs().compareTo(policy.maxSafeAbsMicropriceOffsetBps()) > 0) {
            invalid.add(BookReasonCodes.BOOK_MICROPRICE_OFFSET_OUT_OF_SAFE_RANGE);
        }
        if (!invalid.isEmpty()) {
            return EvidenceAssessment.untrusted(EvidenceDimension.BOOK, concat(inherited, invalid));
        }

        // 6. Indicator availability: insufficient depth drops top5; a missing indicator is never neutral.
        boolean insufficientDepth = book != null && book.levelsUsed() < policy.minimumLevelsUsed();
        BigDecimal top5Imbalance = (book == null || insufficientDepth) ? null : book.top5Imbalance();
        List<ReasonCode> notes = new ArrayList<>(2);
        if (insufficientDepth) {
            notes.add(BookReasonCodes.BOOK_INSUFFICIENT_DEPTH);
        }
        if (top5Imbalance == null && micropriceOffsetBps == null) {
            notes.add(BookReasonCodes.BOOK_INDICATORS_MISSING);
            return EvidenceAssessment.unavailable(EvidenceDimension.BOOK, concat(inherited, notes));
        }

        // 7. Direction and strength from the usable indicators.
        IndicatorReading top5 = top5Imbalance == null ? null
                : new IndicatorReading(
                        direction(top5Imbalance, policy.bullishTop5ImbalanceThreshold(),
                                policy.bearishTop5ImbalanceThreshold()),
                        EvidenceStrength.of(top5Imbalance.abs()));
        IndicatorReading microprice = micropriceOffsetBps == null ? null
                : new IndicatorReading(
                        direction(micropriceOffsetBps, policy.bullishMicropriceOffsetBpsThreshold(),
                                policy.bearishMicropriceOffsetBpsThreshold()),
                        saturatingStrength(micropriceOffsetBps.abs(), policy.fullStrengthAbsMicropriceOffsetBps()));

        if (top5 == null || microprice == null) {
            IndicatorReading single = top5 != null ? top5 : microprice;
            notes.add(BookReasonCodes.BOOK_PARTIAL_EVIDENCE);
            notes.add(directionCode(single.direction()));
            return EvidenceAssessment.available(EvidenceDimension.BOOK, single.direction(),
                    single.direction().isDirectional() ? single.strength() : EvidenceStrength.MIN,
                    concat(inherited, notes));
        }
        return combined(top5, microprice, inherited, notes);
    }

    /** The verdict when both indicators are usable (see the class doc combination table). */
    private static EvidenceAssessment combined(IndicatorReading top5, IndicatorReading microprice,
                                               List<ReasonCode> inherited, List<ReasonCode> notes) {
        InterpretationDirection a = top5.direction();
        InterpretationDirection b = microprice.direction();
        if (a.isDirectional() && b.isDirectional() && a != b) {
            notes.add(BookReasonCodes.BOOK_INDICATORS_CONFLICT);
            return EvidenceAssessment.available(EvidenceDimension.BOOK, InterpretationDirection.MIXED,
                    null, concat(inherited, notes));
        }
        if (a == b && a.isDirectional()) {
            notes.add(directionCode(a));
            return EvidenceAssessment.available(EvidenceDimension.BOOK, a,
                    top5.strength().compareTo(microprice.strength()) <= 0 ? top5.strength() : microprice.strength(),
                    concat(inherited, notes));
        }
        if (a.isDirectional() || b.isDirectional()) {
            IndicatorReading directional = a.isDirectional() ? top5 : microprice;
            notes.add(directionCode(directional.direction()));
            return EvidenceAssessment.available(EvidenceDimension.BOOK, directional.direction(),
                    directional.strength(), concat(inherited, notes));
        }
        notes.add(BookReasonCodes.BOOK_NEUTRAL);
        return EvidenceAssessment.available(EvidenceDimension.BOOK, InterpretationDirection.NEUTRAL,
                EvidenceStrength.MIN, concat(inherited, notes));
    }

    /** One indicator's directional reading and its (always computed) strength. */
    private record IndicatorReading(InterpretationDirection direction, EvidenceStrength strength) {
    }

    /** Inclusive on the directional side: {@code value >= bullish → BULLISH}, {@code <= bearish → BEARISH}. */
    private static InterpretationDirection direction(BigDecimal value, BigDecimal bullish, BigDecimal bearish) {
        if (value.compareTo(bullish) >= 0) {
            return InterpretationDirection.BULLISH;
        }
        if (value.compareTo(bearish) <= 0) {
            return InterpretationDirection.BEARISH;
        }
        return InterpretationDirection.NEUTRAL;
    }

    private static ReasonCode directionCode(InterpretationDirection direction) {
        return switch (direction) {
            case BULLISH -> BookReasonCodes.BOOK_BULLISH;
            case BEARISH -> BookReasonCodes.BOOK_BEARISH;
            case NEUTRAL -> BookReasonCodes.BOOK_NEUTRAL;
            case MIXED, UNKNOWN -> throw new IllegalArgumentException(direction + " has no single direction code");
        };
    }

    /**
     * Book-specific quality faults of the upstream snapshot, in deterministic order; empty when the
     * book state is trustworthy. Deliberately ignores {@code staleTrades} and any degradation that
     * does not concern the book. Package-visible for tests.
     */
    static List<ReasonCode> bookQualityFaults(FeatureQuality quality) {
        requireNonNull(quality, "snapshot.quality");
        List<ReasonCode> faults = new ArrayList<>(4);
        if (!quality.sourceOrderBookTrusted()) {
            faults.add(BookReasonCodes.BOOK_SOURCE_UNTRUSTED);
        }
        if (quality.syncStatus() != SyncStatus.IN_SYNC) {
            faults.add(BookReasonCodes.BOOK_NOT_IN_SYNC);
        }
        if (quality.staleOrderBookState()) {
            faults.add(BookReasonCodes.BOOK_STALE);
        }
        if (quality.incompleteBook()) {
            faults.add(BookReasonCodes.BOOK_INCOMPLETE);
        }
        return faults;
    }

    /**
     * Structurally impossible BBO geometry, checked only over the values that are present: prices,
     * mid and microprice must be positive, the book must not be crossed ({@code bid <= ask}), spread
     * and quantities must not be negative, and a present microprice must lie within
     * {@code [bid, ask]}. A wide but valid spread is not invalid — spread magnitude is never a
     * directional vote here. {@code null} BBO has no geometry to violate. Package-visible for tests.
     */
    static boolean bboGeometryInvalid(BboFeature bbo) {
        if (bbo == null) {
            return false;
        }
        BigDecimal bid = bbo.bestBidPrice();
        BigDecimal ask = bbo.bestAskPrice();
        if (bid != null && bid.signum() <= 0) {
            return true;
        }
        if (ask != null && ask.signum() <= 0) {
            return true;
        }
        if (bid != null && ask != null && bid.compareTo(ask) > 0) {
            return true;
        }
        if (bbo.spreadAbs() != null && bbo.spreadAbs().signum() < 0) {
            return true;
        }
        if (bbo.spreadBps() != null && bbo.spreadBps().signum() < 0) {
            return true;
        }
        if (bbo.bestBidQty() != null && bbo.bestBidQty().signum() < 0) {
            return true;
        }
        if (bbo.bestAskQty() != null && bbo.bestAskQty().signum() < 0) {
            return true;
        }
        if (bbo.midPrice() != null && bbo.midPrice().signum() <= 0) {
            return true;
        }
        BigDecimal microprice = bbo.micropriceTop1();
        if (microprice != null && microprice.signum() <= 0) {
            return true;
        }
        return bid != null && ask != null && microprice != null
                && (microprice.compareTo(bid) < 0 || microprice.compareTo(ask) > 0);
    }

    /** {@code value ∉ [-1, 1]}; a {@code null} value has no range to violate. */
    static boolean outsideUnitRange(BigDecimal value) {
        return value != null
                && (value.compareTo(MINUS_ONE) < 0 || value.compareTo(BigDecimal.ONE) > 0);
    }

    /**
     * {@code min(1, absOffsetBps / fullStrengthAbsMicropriceOffsetBps)} as an exact-enough decimal
     * (scale {@link #STRENGTH_SCALE}, {@link RoundingMode#DOWN}): saturates to
     * {@link EvidenceStrength#MAX} exactly at and beyond full strength. Package-visible for tests.
     */
    static EvidenceStrength saturatingStrength(BigDecimal absOffsetBps, BigDecimal fullStrengthAbsOffsetBps) {
        if (absOffsetBps.compareTo(fullStrengthAbsOffsetBps) >= 0) {
            return EvidenceStrength.MAX;
        }
        return EvidenceStrength.of(absOffsetBps.divide(fullStrengthAbsOffsetBps, STRENGTH_SCALE, STRENGTH_ROUNDING));
    }

    /** Inherited eligibility reasons first, then the book reasons; duplicate-free, insertion-ordered, immutable. */
    private static List<ReasonCode> concat(List<ReasonCode> inherited, List<ReasonCode> book) {
        Set<ReasonCode> merged = new LinkedHashSet<>(inherited);
        merged.addAll(book);
        return List.copyOf(merged);
    }
}
