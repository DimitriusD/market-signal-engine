package com.trading.marketsignalengine.application.domain.interpretation.horizon;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;

import com.trading.marketsignalengine.application.domain.interpretation.EvidenceAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.HorizonEligibility;
import com.trading.marketsignalengine.application.domain.interpretation.ReasonCode;
import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessments;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.quality.SnapshotQualityConsistencyGuard;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessment;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessmentEvaluator;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessments;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, deterministic Stage 6 orchestrator and the <b>single safe public entry point</b> from one
 * validated {@link MarketFeaturesSnapshot} + its Stage 3 {@link QualityAssessment} to
 * {@link HorizonAssessments}: exactly one {@link HorizonAssessment} per {@link MarketHorizon}, each
 * carrying all four evidence dimensions (FLOW, MOMENTUM, VOLATILITY, BOOK, canonical order). No
 * Spring, Kafka, Avro, infrastructure, {@code Clock}, {@code Instant.now()} or metrics; same input +
 * policy ⇒ value-equal result.
 *
 * <p><b>Snapshot-mixing safety.</b> There is deliberately no public API that accepts independently
 * produced evidence containers — the containers carry no source lineage yet, so such an API would let
 * a caller silently pair evidence computed from different snapshots. Instead this evaluator invokes
 * each of the four evidence evaluators itself, exactly once per {@code evaluate(...)} call, against
 * the same snapshot / assessment pair; every evidence evaluator runs the shared
 * {@link SnapshotQualityConsistencyGuard} (one shared instance here), so a mismatched
 * snapshot/assessment pair fails fast before any horizon is assembled. The direction and regime
 * reducers are package-private on purpose.
 *
 * <h2>Per horizon</h2>
 * <ol>
 *   <li><b>Eligibility precedence.</b> The authoritative verdict is
 *       {@code qualityAssessment.eligibilityOf(horizon)} and the <em>original</em>
 *       {@link HorizonEligibility} (with its reason codes) is stored in the assessment — never
 *       replaced by a fresh empty one. A non-ELIGIBLE horizon: direction UNKNOWN, no strength, no
 *       regime, horizon reasons = the eligibility reasons verbatim (no resolution codes); all four
 *       nested evidence attached, none AVAILABLE ({@link HorizonAssessment} fails fast on that
 *       internal inconsistency). Never NEUTRAL.</li>
 *   <li><b>Direction</b> — {@link HorizonDirectionResolver}: FLOW + MOMENTUM primary (FLOW alone on
 *       1S), BOOK as confirmation/context only, VOLATILITY takes no part.</li>
 *   <li><b>Regime</b> — {@link MarketRegimeResolver}: typed {@code VolatilityLevel} + MOMENTUM;
 *       {@code MarketRegime.UNKNOWN} = assessed but not classifiable, {@code null} regime only on a
 *       non-eligible horizon.</li>
 *   <li><b>Horizon reasons</b> describe the resolution only (direction, then book context, then
 *       regime); nested evidence keeps its own explanations and is never flattened into the horizon
 *       reason list. The typed volatility level is consumed by the regime resolver <em>before</em>
 *       the volatility evidence is flattened into the generic nested list.</li>
 * </ol>
 */
public final class HorizonAssessmentEvaluator {

    private final FlowAssessmentEvaluator flowEvaluator;
    private final MomentumAssessmentEvaluator momentumEvaluator;
    private final VolatilityAssessmentEvaluator volatilityEvaluator;
    private final BookAssessmentEvaluator bookEvaluator;

    public HorizonAssessmentEvaluator() {
        this(new SnapshotQualityConsistencyGuard());
    }

    /**
     * Package-private for tests (a counting guard observes exactly four verifications — one per
     * evidence evaluator — per evaluation); production uses the canonical guard. The guard is shared,
     * never bypassed: there is no unchecked entry point.
     */
    HorizonAssessmentEvaluator(SnapshotQualityConsistencyGuard consistencyGuard) {
        requireNonNull(consistencyGuard, "consistencyGuard");
        this.flowEvaluator = new FlowAssessmentEvaluator(consistencyGuard);
        this.momentumEvaluator = new MomentumAssessmentEvaluator(consistencyGuard);
        this.volatilityEvaluator = new VolatilityAssessmentEvaluator(consistencyGuard);
        this.bookEvaluator = new BookAssessmentEvaluator(consistencyGuard);
    }

    /**
     * All four horizon assessments of one snapshot, in canonical order. Each evidence evaluator is
     * invoked exactly once, against this snapshot / assessment pair.
     */
    public HorizonAssessments evaluate(MarketFeaturesSnapshot snapshot,
                                       QualityAssessment qualityAssessment,
                                       HorizonInterpretationPolicy policy) {
        requireNonNull(snapshot, "snapshot");
        requireNonNull(qualityAssessment, "qualityAssessment");
        requireNonNull(policy, "horizon policy");

        FlowAssessments flow = flowEvaluator.evaluate(snapshot, qualityAssessment, policy.flowPolicy());
        MomentumAssessments momentum = momentumEvaluator.evaluate(snapshot, qualityAssessment, policy.momentumPolicy());
        VolatilityAssessments volatility =
                volatilityEvaluator.evaluate(snapshot, qualityAssessment, policy.volatilityPolicy());
        BookAssessments book = bookEvaluator.evaluate(snapshot, qualityAssessment, policy.bookPolicy());

        Map<MarketHorizon, HorizonAssessment> result = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            result.put(horizon, assemble(horizon, qualityAssessment.eligibilityOf(horizon),
                    flow.of(horizon), momentum.of(horizon), volatility.of(horizon), book.of(horizon)));
        }
        return new HorizonAssessments(result);
    }

    /**
     * One horizon from its typed evidence. Package-visible for unit tests only — the safe public
     * boundary is {@link #evaluate}, which derives every argument from one snapshot.
     */
    static HorizonAssessment assemble(MarketHorizon horizon,
                                      HorizonEligibility eligibility,
                                      EvidenceAssessment flow,
                                      EvidenceAssessment momentum,
                                      VolatilityAssessment volatility,
                                      EvidenceAssessment book) {
        requireNonNull(horizon, "horizon");
        requireNonNull(eligibility, "eligibility");
        requireNonNull(volatility, "volatility assessment");
        List<EvidenceAssessment> evidence = List.of(flow, momentum, volatility.evidence(), book);
        if (!eligibility.isEligible()) {
            // the original eligibility (status + reasons) is kept; the HorizonAssessment invariants
            // reject any AVAILABLE nested evidence on a non-eligible horizon
            return HorizonAssessment.notEligible(horizon, eligibility, evidence, eligibility.reasonCodes());
        }
        HorizonDirectionResolution direction = HorizonDirectionResolver.resolve(horizon, flow, momentum, book);
        // the typed level drives the regime before the volatility evidence is flattened
        MarketRegimeResolution regime = MarketRegimeResolver.resolve(volatility, momentum);
        List<ReasonCode> reasons = new ArrayList<>(direction.reasonCodes());
        reasons.addAll(regime.reasonCodes());
        return new HorizonAssessment(horizon, eligibility, direction.direction(), direction.evidenceStrength(),
                regime.regime(), evidence, reasons);
    }
}
