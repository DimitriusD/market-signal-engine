package com.trading.marketsignalengine.application.domain.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * Signal-level summary of the directional setup formed on a snapshot, intended for a future
 * strategy-service. This is <strong>not</strong> an order intent: {@code side == LONG} means a long
 * setup has formed on the market, not "buy now".
 */
public record MarketSetup(
        SetupSide side,
        SetupType type,
        SignalStrength strength,
        BigDecimal confidence,
        String reason,
        Map<String, String> attributes) {

    public MarketSetup {
        if (side == null) {
            side = SetupSide.NONE;
        }
        if (type == null) {
            type = SetupType.NONE;
        }
        if (strength == null) {
            strength = SignalStrength.NONE;
        }
        if (confidence == null) {
            confidence = BigDecimal.ZERO;
        }
        if (attributes == null || attributes.isEmpty()) {
            attributes = Collections.emptyMap();
        } else {
            attributes = Map.copyOf(attributes);
        }
    }

    public static MarketSetup none(String reason) {
        return new MarketSetup(
                SetupSide.NONE,
                SetupType.NONE,
                SignalStrength.NONE,
                BigDecimal.ZERO,
                reason,
                null
        );
    }

    public boolean hasDirectionalSetup() {
        return side == SetupSide.LONG || side == SetupSide.SHORT;
    }
}
