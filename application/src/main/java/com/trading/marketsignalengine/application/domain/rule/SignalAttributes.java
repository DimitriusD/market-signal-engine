package com.trading.marketsignalengine.application.domain.rule;

import java.math.BigDecimal;
import java.util.Map;

final class SignalAttributes {

    private SignalAttributes() {
    }

    static void putIfPresent(Map<String, String> attributes, String key, BigDecimal value) {
        if (value != null) {
            attributes.put(key, value.toPlainString());
        }
    }

    static void putIfPresent(Map<String, String> attributes, String key, Long value) {
        if (value != null) {
            attributes.put(key, value.toString());
        }
    }

    static void putIfPresent(Map<String, String> attributes, String key, Integer value) {
        if (value != null) {
            attributes.put(key, value.toString());
        }
    }

    static void putIfPresent(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    static void putBoolean(Map<String, String> attributes, String key, boolean value) {
        attributes.put(key, Boolean.toString(value));
    }

    static void putInt(Map<String, String> attributes, String key, int value) {
        attributes.put(key, Integer.toString(value));
    }
}
