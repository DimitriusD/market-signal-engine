package com.trading.marketsignalengine.event.config;

import com.trading.marketsignalengine.application.port.output.SignalMetricsPort;
import com.trading.marketsignalengine.event.metrics.DeadLetterMetrics;
import com.trading.marketsignalengine.event.metrics.MicrometerSignalMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds the application's metrics port to Micrometer (registry provided by Boot actuator). */
@Configuration
public class MetricsConfiguration {

    @Bean
    public SignalMetricsPort signalMetricsPort(MeterRegistry meterRegistry, Clock clock) {
        return new MicrometerSignalMetrics(meterRegistry, clock);
    }

    @Bean
    public DeadLetterMetrics deadLetterMetrics(MeterRegistry meterRegistry) {
        return new DeadLetterMetrics(meterRegistry);
    }
}
