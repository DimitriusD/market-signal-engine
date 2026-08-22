package com.trading.marketsignalengine.application.domain.model;

import java.math.BigDecimal;
import lombok.Builder;

@Builder(toBuilder = true)
public record SignalConfiguration(
        String signalSetVersion,
        BigDecimal maxSpreadBps,
        BigDecimal buyFlowImbalance5sThreshold,
        BigDecimal sellFlowImbalance5sThreshold,
        int minTradeCount5sForTradeFlowSignal,
        BigDecimal buyBookImbalanceThreshold,
        BigDecimal sellBookImbalanceThreshold,
        BigDecimal maxRealizedVolatilityBps1s,
        long microstructureSetupTtlMs,
        long riskOffTtlMs,
        long neutralTtlMs) {

    public SignalConfiguration {
        if (signalSetVersion == null || signalSetVersion.isBlank()) {
            throw new IllegalArgumentException("signalSetVersion must not be blank");
        }
        if (maxSpreadBps == null || maxSpreadBps.signum() <= 0) {
            throw new IllegalArgumentException("maxSpreadBps must be positive");
        }
        if (buyFlowImbalance5sThreshold == null || buyFlowImbalance5sThreshold.signum() <= 0) {
            throw new IllegalArgumentException("buyFlowImbalance5sThreshold must be positive");
        }
        if (sellFlowImbalance5sThreshold == null || sellFlowImbalance5sThreshold.signum() >= 0) {
            throw new IllegalArgumentException("sellFlowImbalance5sThreshold must be negative");
        }
        if (buyFlowImbalance5sThreshold.compareTo(sellFlowImbalance5sThreshold) <= 0) {
            throw new IllegalArgumentException(
                    "buyFlowImbalance5sThreshold must be greater than sellFlowImbalance5sThreshold");
        }
        if (minTradeCount5sForTradeFlowSignal < 0) {
            throw new IllegalArgumentException("minTradeCount5sForTradeFlowSignal must be >= 0");
        }
        if (buyBookImbalanceThreshold == null || sellBookImbalanceThreshold == null
                || buyBookImbalanceThreshold.compareTo(sellBookImbalanceThreshold) <= 0) {
            throw new IllegalArgumentException(
                    "buyBookImbalanceThreshold must be greater than sellBookImbalanceThreshold");
        }
        if (maxRealizedVolatilityBps1s == null || maxRealizedVolatilityBps1s.signum() < 0) {
            throw new IllegalArgumentException("maxRealizedVolatilityBps1s must be non-negative");
        }
        if (microstructureSetupTtlMs <= 0) {
            throw new IllegalArgumentException("microstructureSetupTtlMs must be positive");
        }
        if (riskOffTtlMs <= 0) {
            throw new IllegalArgumentException("riskOffTtlMs must be positive");
        }
        if (neutralTtlMs <= 0) {
            throw new IllegalArgumentException("neutralTtlMs must be positive");
        }
    }

    /**
     * {@code maxRealizedVolatilityBps1s} is an uncalibrated placeholder: generous on purpose, it
     * blocks only clearly extreme 1s regimes. The calibrated value comes from the first replay pass
     * over recorded MFS v2 data (path-to-paper-trading.md, decision 8.2).
     */
    public static SignalConfiguration defaults() {
        return SignalConfiguration.builder()
                .signalSetVersion("mse-signals-v8")
                .maxSpreadBps(new BigDecimal("2.0"))
                .buyFlowImbalance5sThreshold(new BigDecimal("0.15"))
                .sellFlowImbalance5sThreshold(new BigDecimal("-0.15"))
                .minTradeCount5sForTradeFlowSignal(10)
                .buyBookImbalanceThreshold(new BigDecimal("0.60"))
                .sellBookImbalanceThreshold(new BigDecimal("-0.60"))
                .maxRealizedVolatilityBps1s(new BigDecimal("50.0"))
                .microstructureSetupTtlMs(2_000L)
                .riskOffTtlMs(5_000L)
                .neutralTtlMs(1_000L)
                .build();
    }
}
