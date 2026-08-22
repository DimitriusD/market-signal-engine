package com.trading.marketsignalengine.event.metrics;

import com.trading.marketsignalengine.application.domain.model.MarketSignal;
import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalDirection;
import com.trading.marketsignalengine.application.domain.model.SignalType;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.port.output.SignalMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Micrometer implementation of {@link SignalMetricsPort}. Answers "why was / wasn't there a signal"
 * without reading logs:
 * <ul>
 *   <li>{@code mse.snapshots} counter, tags {@code riskLevel}, {@code marketBias}, {@code setupSide}</li>
 *   <li>{@code mse.no_trade.reasons} counter, tag {@code type} — one increment per RISK_OFF signal type
 *       in a no-trade snapshot (excluding the engine's summary NO_TRADE_CONDITION)</li>
 *   <li>{@code mse.input.age} summary (ms): evaluatedAt − exchange event time — how old the market
 *       picture was when the engine judged it (MFS compute + transport + queueing)</li>
 *   <li>{@code mse.evaluate.duration} timer: validate + evaluate</li>
 *   <li>{@code mse.publish.duration} timer, tag {@code outcome=ok|failed}</li>
 *   <li>{@code mse.e2e.latency} summary (ms): publish acknowledged − exchange event time</li>
 * </ul>
 * All tags are bounded enums; no instrument-level tags (cardinality stays constant per deployment).
 */
public final class MicrometerSignalMetrics implements SignalMetricsPort {

    static final String SNAPSHOTS = "mse.snapshots";
    static final String NO_TRADE_REASONS = "mse.no_trade.reasons";
    static final String INPUT_AGE = "mse.input.age";
    static final String EVALUATE_DURATION = "mse.evaluate.duration";
    static final String PUBLISH_DURATION = "mse.publish.duration";
    static final String E2E_LATENCY = "mse.e2e.latency";

    private final MeterRegistry registry;
    private final Clock clock;

    public MicrometerSignalMetrics(MeterRegistry registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void evaluated(MarketFeaturesSnapshot features, MarketSignalSnapshot snapshot, Duration evaluation) {
        Timer.builder(EVALUATE_DURATION)
                .description("validate + evaluate time per input snapshot")
                .register(registry)
                .record(evaluation);

        Counter.builder(SNAPSHOTS)
                .description("Signal snapshots produced")
                .tag("riskLevel", name(snapshot.riskLevel()))
                .tag("marketBias", name(snapshot.marketBias()))
                .tag("setupSide", snapshot.setup() == null ? "NONE" : name(snapshot.setup().side()))
                .register(registry)
                .increment();

        if (snapshot.signals() != null) {
            for (MarketSignal signal : snapshot.signals()) {
                if (signal.direction() == SignalDirection.RISK_OFF && signal.type() != SignalType.NO_TRADE_CONDITION) {
                    Counter.builder(NO_TRADE_REASONS)
                            .description("RISK_OFF signal types that produced a no-trade snapshot")
                            .tag("type", name(signal.type()))
                            .register(registry)
                            .increment();
                }
            }
        }

        if (features.eventTime() != null && snapshot.createdAt() != null) {
            DistributionSummary.builder(INPUT_AGE)
                    .description("evaluatedAt - exchange event time, ms")
                    .baseUnit("milliseconds")
                    .register(registry)
                    .record(Math.max(0L, Duration.between(features.eventTime(), snapshot.createdAt()).toMillis()));
        }
    }

    @Override
    public void published(MarketSignalSnapshot snapshot, Duration publish) {
        Timer.builder(PUBLISH_DURATION)
                .description("time until the output broker acknowledged the snapshot")
                .tag("outcome", "ok")
                .register(registry)
                .record(publish);

        if (snapshot.eventTime() != null) {
            DistributionSummary.builder(E2E_LATENCY)
                    .description("publish acknowledged - exchange event time, ms")
                    .baseUnit("milliseconds")
                    .register(registry)
                    .record(Math.max(0L, Duration.between(snapshot.eventTime(), Instant.now(clock)).toMillis()));
        }
    }

    @Override
    public void publishFailed(MarketSignalSnapshot snapshot, Duration publish, Throwable error) {
        Timer.builder(PUBLISH_DURATION)
                .description("time until the output broker acknowledged the snapshot")
                .tag("outcome", "failed")
                .register(registry)
                .record(publish);
    }

    private static String name(Enum<?> value) {
        return value == null ? "UNKNOWN" : value.name();
    }
}
