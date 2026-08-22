package com.trading.marketsignalengine.application.port.output;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.time.Duration;

/**
 * Output port for operational metrics of the handle path. The application core reports <em>what
 * happened</em> (evaluated, published, publish failed) with the durations it measured; the adapter
 * decides how to expose it (Micrometer in production, no-op in tests). Keeping this a port keeps
 * the core free of any metrics framework and lets the replay harness run without one.
 */
public interface SignalMetricsPort {

    SignalMetricsPort NOOP = new SignalMetricsPort() {
        @Override
        public void evaluated(MarketFeaturesSnapshot features, MarketSignalSnapshot snapshot, Duration evaluation) {
        }

        @Override
        public void published(MarketSignalSnapshot snapshot, Duration publish) {
        }

        @Override
        public void publishFailed(MarketSignalSnapshot snapshot, Duration publish, Throwable error) {
        }
    };

    /** Engine produced a snapshot for the given input; {@code evaluation} is validate+evaluate time. */
    void evaluated(MarketFeaturesSnapshot features, MarketSignalSnapshot snapshot, Duration evaluation);

    /** Snapshot was acknowledged by the output transport. */
    void published(MarketSignalSnapshot snapshot, Duration publish);

    /** Output transport rejected or timed out on the snapshot; the caller re-throws. */
    void publishFailed(MarketSignalSnapshot snapshot, Duration publish, Throwable error);
}
