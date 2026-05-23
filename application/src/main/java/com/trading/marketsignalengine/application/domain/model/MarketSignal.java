package com.trading.marketsignalengine.application.domain.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record MarketSignal(
        SignalType type,
        SignalDirection direction,
        SignalStrength strength,
        BigDecimal confidence,
        String reason,
        Map<String, String> attributes) {

    public MarketSignal {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(reason, "reason");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static MarketSignal bullish(
            SignalType type,
            SignalStrength strength,
            BigDecimal confidence,
            String reason,
            Map<String, String> attributes) {
        return new MarketSignal(type, SignalDirection.BULLISH, strength, confidence, reason, safeAttributes(attributes));
    }

    public static MarketSignal bearish(
            SignalType type,
            SignalStrength strength,
            BigDecimal confidence,
            String reason,
            Map<String, String> attributes) {
        return new MarketSignal(type, SignalDirection.BEARISH, strength, confidence, reason, safeAttributes(attributes));
    }

    public static MarketSignal neutral(
            SignalType type,
            SignalStrength strength,
            BigDecimal confidence,
            String reason,
            Map<String, String> attributes) {
        return new MarketSignal(type, SignalDirection.NEUTRAL, strength, confidence, reason, safeAttributes(attributes));
    }

    public static MarketSignal riskOff(
            SignalType type,
            SignalStrength strength,
            BigDecimal confidence,
            String reason,
            Map<String, String> attributes) {
        return new MarketSignal(type, SignalDirection.RISK_OFF, strength, confidence, reason, safeAttributes(attributes));
    }

    private static Map<String, String> safeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyMap();
        }
        return Map.copyOf(attributes);
    }
}
