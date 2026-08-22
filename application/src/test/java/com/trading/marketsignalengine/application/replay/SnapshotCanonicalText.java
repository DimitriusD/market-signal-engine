package com.trading.marketsignalengine.application.replay;

import com.trading.marketsignalengine.application.domain.model.MarketSetup;
import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical, line-oriented text rendering of a {@link MarketSignalSnapshot} for golden comparison.
 * Every field that downstream can observe is rendered; attribute maps are sorted by key because
 * {@code Map.copyOf} does not preserve insertion order. The format is intentionally diff-friendly:
 * one fact per line, stable ordering, no timestamps other than the ones the snapshot carries.
 */
final class SnapshotCanonicalText {

    private SnapshotCanonicalText() {
    }

    static String render(MarketSignalSnapshot s) {
        StringBuilder out = new StringBuilder(2048);
        line(out, "signalSnapshotId", s.signalSnapshotId());
        line(out, "sourceFeatureSnapshotId", s.sourceFeatureSnapshotId());
        line(out, "instrumentId", s.instrumentId());
        line(out, "exchange", s.exchange());
        line(out, "marketType", s.marketType());
        line(out, "base", s.base());
        line(out, "quote", s.quote());
        line(out, "symbol", s.symbol());
        line(out, "eventTime", s.eventTime());
        line(out, "evaluatedAt", s.createdAt());
        line(out, "validUntil", s.validUntil());
        line(out, "ttlMs", s.ttlMs());
        line(out, "sourceFeatureSetVersion", s.sourceFeatureSetVersion());
        line(out, "signalSetVersion", s.signalSetVersion());
        line(out, "marketBias", s.marketBias());
        line(out, "marketBiasScore", plain(s.marketBiasScore()));
        line(out, "riskLevel", s.riskLevel());

        MarketSetup setup = s.setup();
        if (setup == null) {
            line(out, "setup", null);
        } else {
            line(out, "setup.side", setup.side());
            line(out, "setup.type", setup.type());
            line(out, "setup.strength", setup.strength());
            line(out, "setup.confidence", plain(setup.confidence()));
            line(out, "setup.reason", setup.reason());
            attributes(out, "setup.attr", setup.attributes());
        }

        line(out, "signals.count", s.signals() == null ? 0 : s.signals().size());
        if (s.signals() != null) {
            int i = 0;
            for (MarketSignal signal : s.signals()) {
                String p = "signal[" + i + "]";
                line(out, p + ".type", signal.type());
                line(out, p + ".direction", signal.direction());
                line(out, p + ".strength", signal.strength());
                line(out, p + ".confidence", plain(signal.confidence()));
                line(out, p + ".reason", signal.reason());
                attributes(out, p + ".attr", signal.attributes());
                i++;
            }
        }
        return out.toString();
    }

    private static void attributes(StringBuilder out, String prefix, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> e : new TreeMap<>(attributes).entrySet()) {
            line(out, prefix + "." + e.getKey(), e.getValue());
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static void line(StringBuilder out, String key, Object value) {
        out.append(key).append('=').append(value == null ? "<null>" : value.toString()).append('\n');
    }
}
