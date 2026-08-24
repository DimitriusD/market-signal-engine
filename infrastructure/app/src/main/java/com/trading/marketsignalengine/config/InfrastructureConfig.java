package com.trading.marketsignalengine.config;

import com.trading.marketsignalengine.application.domain.interpretation.assembly.InterpretationValidityPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationAssemblyPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.assembly.MarketInterpretationSnapshotAssembler;
import com.trading.marketsignalengine.application.domain.interpretation.book.BookAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.cross.CrossHorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.flow.FlowHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.horizon.HorizonInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.momentum.MomentumHorizonPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityInterpretationPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityAssessmentResolver;
import com.trading.marketsignalengine.application.domain.interpretation.quality.QualityEligibilityPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityAssessmentPolicy;
import com.trading.marketsignalengine.application.domain.interpretation.volatility.VolatilityHorizonPolicy;
import com.trading.marketsignalengine.application.domain.model.MarketHorizon;
import com.trading.marketsignalengine.application.domain.validation.MarketFeaturesSnapshotValidator;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationSnapshotPublisherPort;
import com.trading.marketsignalengine.application.service.MarketInterpretationHandleService;
import com.trading.marketsignalengine.application.service.ValidatedMarketInterpretationEvaluator;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the V2 multi-horizon interpretation runtime. Every domain policy is built from
 * the explicit {@link InterpretationProperties} — no defaults are invented here or in the domain: a
 * missing property fails startup with the offending property name ({@link IllegalStateException}),
 * an invalid value fails startup with the domain policy's own invariant message. The resulting
 * policies are immutable domain values; live and replay share the one
 * {@link ValidatedMarketInterpretationEvaluator}, so production wiring and replayed wiring cannot
 * drift apart.
 */
@Configuration
@EnableConfigurationProperties(InterpretationProperties.class)
public class InfrastructureConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public MarketFeaturesSnapshotValidator marketFeaturesSnapshotValidator(InterpretationProperties properties) {
        List<String> configured = properties.supportedFeatureSetVersions();
        Set<String> supported = configured == null || configured.isEmpty()
                ? Set.of("mfs-features-v2")
                : new LinkedHashSet<>(configured);
        return new MarketFeaturesSnapshotValidator(supported);
    }

    // ------------------------------------------------------------------ policies from properties

    @Bean
    public QualityEligibilityPolicy qualityEligibilityPolicy(InterpretationProperties properties) {
        InterpretationProperties.Quality quality = req(properties.quality(), "app.interpretation.quality");
        return QualityEligibilityPolicy.of(
                millis(quality.maxFeatureAgeMs(), "app.interpretation.quality.max-feature-age-ms"),
                millis(quality.maxProcessingLatencyMs(), "app.interpretation.quality.max-processing-latency-ms"),
                req(quality.blockFutureEvents(), "app.interpretation.quality.block-future-events"));
    }

    @Bean
    public HorizonInterpretationPolicy horizonInterpretationPolicy(InterpretationProperties properties) {
        InterpretationProperties.Flow flow = req(properties.flow(), "app.interpretation.flow");
        InterpretationProperties.Momentum momentum = req(properties.momentum(), "app.interpretation.momentum");
        InterpretationProperties.Volatility volatility = req(properties.volatility(), "app.interpretation.volatility");
        InterpretationProperties.Book book = req(properties.book(), "app.interpretation.book");
        InterpretationProperties.Horizon horizon = req(properties.horizon(), "app.interpretation.horizon");

        Function<MarketHorizon, FlowHorizonPolicy> flowOf = h -> {
            InterpretationProperties.FlowHorizon p =
                    horizonEntry(flow.horizons(), h, "app.interpretation.flow.horizons");
            return FlowHorizonPolicy.of(h,
                    req(p.bullishImbalanceThreshold(), flowKey(h, "bullish-imbalance-threshold")),
                    req(p.bearishImbalanceThreshold(), flowKey(h, "bearish-imbalance-threshold")),
                    req(p.minTradeCount(), flowKey(h, "min-trade-count")),
                    req(p.minAggressiveTradeCount(), flowKey(h, "min-aggressive-trade-count")),
                    req(p.maxUnknownSideRatio(), flowKey(h, "max-unknown-side-ratio")));
        };
        Function<MarketHorizon, MomentumHorizonPolicy> momentumOf = h -> {
            InterpretationProperties.MomentumHorizon p =
                    horizonEntry(momentum.horizons(), h, "app.interpretation.momentum.horizons");
            return MomentumHorizonPolicy.of(h,
                    req(p.bullishPriceChangeBpsThreshold(), momentumKey(h, "bullish-price-change-bps-threshold")),
                    req(p.bearishPriceChangeBpsThreshold(), momentumKey(h, "bearish-price-change-bps-threshold")),
                    req(p.fullStrengthAbsMoveBps(), momentumKey(h, "full-strength-abs-move-bps")),
                    req(p.maxSafeAbsMoveBps(), momentumKey(h, "max-safe-abs-move-bps")));
        };
        Function<MarketHorizon, VolatilityHorizonPolicy> volatilityOf = h -> {
            InterpretationProperties.VolatilityHorizon p =
                    horizonEntry(volatility.horizons(), h, "app.interpretation.volatility.horizons");
            return VolatilityHorizonPolicy.of(h,
                    req(p.lowUpperBoundBps(), volatilityKey(h, "low-upper-bound-bps")),
                    req(p.normalUpperBoundBps(), volatilityKey(h, "normal-upper-bound-bps")),
                    req(p.highUpperBoundBps(), volatilityKey(h, "high-upper-bound-bps")));
        };

        return new HorizonInterpretationPolicy(
                req(horizon.policyVersion(), "app.interpretation.horizon.policy-version"),
                FlowAssessmentPolicy.of(req(flow.policyVersion(), "app.interpretation.flow.policy-version"),
                        flowOf.apply(MarketHorizon.H1S), flowOf.apply(MarketHorizon.H5S),
                        flowOf.apply(MarketHorizon.H15S), flowOf.apply(MarketHorizon.H60S)),
                MomentumAssessmentPolicy.of(req(momentum.policyVersion(), "app.interpretation.momentum.policy-version"),
                        momentumOf.apply(MarketHorizon.H5S), momentumOf.apply(MarketHorizon.H15S),
                        momentumOf.apply(MarketHorizon.H60S)),
                VolatilityAssessmentPolicy.of(
                        req(volatility.policyVersion(), "app.interpretation.volatility.policy-version"),
                        volatilityOf.apply(MarketHorizon.H1S), volatilityOf.apply(MarketHorizon.H5S),
                        volatilityOf.apply(MarketHorizon.H15S), volatilityOf.apply(MarketHorizon.H60S)),
                new BookAssessmentPolicy(
                        req(book.policyVersion(), "app.interpretation.book.policy-version"),
                        req(book.minimumLevelsUsed(), "app.interpretation.book.minimum-levels-used"),
                        req(book.bullishTop5ImbalanceThreshold(),
                                "app.interpretation.book.bullish-top5-imbalance-threshold"),
                        req(book.bearishTop5ImbalanceThreshold(),
                                "app.interpretation.book.bearish-top5-imbalance-threshold"),
                        req(book.bullishMicropriceOffsetBpsThreshold(),
                                "app.interpretation.book.bullish-microprice-offset-bps-threshold"),
                        req(book.bearishMicropriceOffsetBpsThreshold(),
                                "app.interpretation.book.bearish-microprice-offset-bps-threshold"),
                        req(book.fullStrengthAbsMicropriceOffsetBps(),
                                "app.interpretation.book.full-strength-abs-microprice-offset-bps"),
                        req(book.maxSafeAbsMicropriceOffsetBps(),
                                "app.interpretation.book.max-safe-abs-microprice-offset-bps")));
    }

    @Bean
    public OpportunityInterpretationPolicy opportunityInterpretationPolicy(
            InterpretationProperties properties, HorizonInterpretationPolicy horizonInterpretationPolicy) {
        InterpretationProperties.CrossHorizon cross =
                req(properties.crossHorizon(), "app.interpretation.cross-horizon");
        InterpretationProperties.Opportunity opportunity =
                req(properties.opportunity(), "app.interpretation.opportunity");
        return new OpportunityInterpretationPolicy(
                req(opportunity.policyVersion(), "app.interpretation.opportunity.policy-version"),
                new CrossHorizonInterpretationPolicy(
                        req(cross.policyVersion(), "app.interpretation.cross-horizon.policy-version"),
                        horizonInterpretationPolicy),
                req(opportunity.allowVolatileMomentumContinuation(),
                        "app.interpretation.opportunity.allow-volatile-momentum-continuation"));
    }

    @Bean
    public InterpretationValidityPolicy interpretationValidityPolicy(InterpretationProperties properties) {
        InterpretationProperties.Validity validity = req(properties.validity(), "app.interpretation.validity");
        Map<String, Long> configured = req(validity.momentumContinuationBaseValidityMs(),
                "app.interpretation.validity.momentum-continuation-base-validity-ms");
        EnumMap<MarketHorizon, Duration> base = new EnumMap<>(MarketHorizon.class);
        for (MarketHorizon horizon : MarketHorizon.canonicalOrder()) {
            base.put(horizon, millis(horizonEntry(configured, horizon,
                            "app.interpretation.validity.momentum-continuation-base-validity-ms"),
                    "app.interpretation.validity.momentum-continuation-base-validity-ms[" + horizon.wireValue() + "]"));
        }
        return new InterpretationValidityPolicy(
                req(validity.policyVersion(), "app.interpretation.validity.policy-version"),
                base,
                millis(validity.noOpportunityBaseValidityMs(),
                        "app.interpretation.validity.no-opportunity-base-validity-ms"),
                millis(validity.blockedBaseValidityMs(), "app.interpretation.validity.blocked-base-validity-ms"),
                millis(validity.publicationSafetyBufferMs(),
                        "app.interpretation.validity.publication-safety-buffer-ms"),
                millis(validity.degradedQualityAdjustmentMs(),
                        "app.interpretation.validity.degraded-quality-adjustment-ms"),
                millis(validity.volatileRegimeAdjustmentMs(),
                        "app.interpretation.validity.volatile-regime-adjustment-ms"));
    }

    @Bean
    public MarketInterpretationAssemblyPolicy marketInterpretationAssemblyPolicy(
            InterpretationProperties properties,
            OpportunityInterpretationPolicy opportunityInterpretationPolicy,
            InterpretationValidityPolicy interpretationValidityPolicy) {
        // the config hash is a real deployment-provided value covering every policy above — the
        // engine never fabricates one (no hashCode(), no serialization; canonical hashing is a later stage)
        return new MarketInterpretationAssemblyPolicy(
                req(properties.version(), "app.interpretation.version"),
                req(properties.configHash(), "app.interpretation.config-hash"),
                opportunityInterpretationPolicy,
                interpretationValidityPolicy);
    }

    // ------------------------------------------------------------------ runtime pipeline

    /**
     * The one validated evaluation step (validate → quality → assemble) shared by the live handler
     * below and by {@code InterpretationReplayHarness}: live and replay cannot diverge on validation
     * or interpretation wiring.
     */
    @Bean
    public ValidatedMarketInterpretationEvaluator marketInterpretationEvaluator(
            MarketFeaturesSnapshotValidator validator,
            QualityEligibilityPolicy qualityEligibilityPolicy,
            MarketInterpretationAssemblyPolicy marketInterpretationAssemblyPolicy) {
        return new ValidatedMarketInterpretationEvaluator(
                validator,
                new QualityAssessmentResolver(),
                new MarketInterpretationSnapshotAssembler(),
                qualityEligibilityPolicy,
                marketInterpretationAssemblyPolicy);
    }

    /** Live handle path: receivedAt = clock.instant() (= quality assessedAt) → evaluator → publisher. */
    @Bean
    public MarketFeaturesHandler marketFeatureHandler(
            ValidatedMarketInterpretationEvaluator marketInterpretationEvaluator,
            MarketInterpretationSnapshotPublisherPort publisher,
            Clock clock) {
        return new MarketInterpretationHandleService(marketInterpretationEvaluator, publisher, clock);
    }

    // ------------------------------------------------------------------ helpers

    private static <T> T req(T value, String property) {
        if (value == null) {
            throw new IllegalStateException(property + " must be explicitly configured");
        }
        return value;
    }

    private static Duration millis(Long value, String property) {
        return Duration.ofMillis(req(value, property));
    }

    /** Resolves a wire-value horizon key ({@code 1S}, ...) case-insensitively; missing key fails startup. */
    private static <T> T horizonEntry(Map<String, T> byHorizon, MarketHorizon horizon, String property) {
        req(byHorizon, property);
        for (Map.Entry<String, T> entry : byHorizon.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase(Locale.ROOT);
            if (horizon.wireValue().equals(key)) {
                return req(entry.getValue(), property + "[" + horizon.wireValue() + "]");
            }
        }
        throw new IllegalStateException(property + " is missing horizon " + horizon.wireValue()
                + " (configured keys: " + byHorizon.keySet() + ")");
    }

    private static String flowKey(MarketHorizon horizon, String suffix) {
        return "app.interpretation.flow.horizons[" + horizon.wireValue() + "]." + suffix;
    }

    private static String momentumKey(MarketHorizon horizon, String suffix) {
        return "app.interpretation.momentum.horizons[" + horizon.wireValue() + "]." + suffix;
    }

    private static String volatilityKey(MarketHorizon horizon, String suffix) {
        return "app.interpretation.volatility.horizons[" + horizon.wireValue() + "]." + suffix;
    }
}
