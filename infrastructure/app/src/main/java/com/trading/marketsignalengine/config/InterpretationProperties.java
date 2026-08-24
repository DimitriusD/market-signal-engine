package com.trading.marketsignalengine.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Explicit configuration of the V2 multi-horizon interpretation runtime ({@code app.interpretation}).
 * Constructor-bound records: immutable after binding, no setters. Deliberately <b>no defaults</b> for
 * any policy value — every threshold, version and duration must be stated in configuration, and a
 * missing / invalid value breaks startup when the domain policies are constructed
 * ({@code InterpretationConfig}); the domain evaluators themselves carry no hidden defaults.
 *
 * <p>{@code configHash} is the {@code interpretationConfigHash} of the published lineage. It must be
 * a <b>real</b> value provided by the deployment (no fictitious default, never {@code hashCode()} or
 * serialization tricks — canonical config hashing is a later stage) and must cover every policy in
 * this configuration: quality, flow, momentum, volatility, book, horizon, cross-horizon, opportunity
 * and validity. Horizon-keyed maps use the contract wire values ({@code 1S}, {@code 5S}, {@code 15S},
 * {@code 60S}) as keys.
 */
@ConfigurationProperties(prefix = "app.interpretation")
public record InterpretationProperties(
        String version,
        String configHash,
        /** Allowlist of upstream {@code featureSetVersion} values; anything else fails closed to the DLT. */
        List<String> supportedFeatureSetVersions,
        Quality quality,
        Flow flow,
        Momentum momentum,
        Volatility volatility,
        Book book,
        Horizon horizon,
        CrossHorizon crossHorizon,
        Opportunity opportunity,
        Validity validity) {

    public record Quality(
            Long maxFeatureAgeMs,
            Long maxProcessingLatencyMs,
            Boolean blockFutureEvents) {
    }

    public record Flow(
            String policyVersion,
            Map<String, FlowHorizon> horizons) {
    }

    public record FlowHorizon(
            BigDecimal bullishImbalanceThreshold,
            BigDecimal bearishImbalanceThreshold,
            Integer minTradeCount,
            Integer minAggressiveTradeCount,
            BigDecimal maxUnknownSideRatio) {
    }

    public record Momentum(
            String policyVersion,
            Map<String, MomentumHorizon> horizons) {
    }

    public record MomentumHorizon(
            BigDecimal bullishPriceChangeBpsThreshold,
            BigDecimal bearishPriceChangeBpsThreshold,
            BigDecimal fullStrengthAbsMoveBps,
            BigDecimal maxSafeAbsMoveBps) {
    }

    public record Volatility(
            String policyVersion,
            Map<String, VolatilityHorizon> horizons) {
    }

    public record VolatilityHorizon(
            BigDecimal lowUpperBoundBps,
            BigDecimal normalUpperBoundBps,
            BigDecimal highUpperBoundBps) {
    }

    public record Book(
            String policyVersion,
            Integer minimumLevelsUsed,
            BigDecimal bullishTop5ImbalanceThreshold,
            BigDecimal bearishTop5ImbalanceThreshold,
            BigDecimal bullishMicropriceOffsetBpsThreshold,
            BigDecimal bearishMicropriceOffsetBpsThreshold,
            BigDecimal fullStrengthAbsMicropriceOffsetBps,
            BigDecimal maxSafeAbsMicropriceOffsetBps) {
    }

    public record Horizon(String policyVersion) {
    }

    public record CrossHorizon(String policyVersion) {
    }

    public record Opportunity(
            String policyVersion,
            /** Whether a VOLATILE regime may still produce a momentum-continuation candidate; must be explicit. */
            Boolean allowVolatileMomentumContinuation) {
    }

    public record Validity(
            String policyVersion,
            Map<String, Long> momentumContinuationBaseValidityMs,
            Long noOpportunityBaseValidityMs,
            Long blockedBaseValidityMs,
            Long publicationSafetyBufferMs,
            Long degradedQualityAdjustmentMs,
            Long volatileRegimeAdjustmentMs) {
    }
}
