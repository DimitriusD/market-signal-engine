package com.trading.marketsignalengine.event.config;

import com.trading.marketsignalengine.event.metrics.DeadLetterMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Transport-level metrics wiring (registry provided by Boot actuator). Only the dead-letter counter
 * remains — the V2 handle path deliberately carries no application metrics port.
 */
@Configuration
public class MetricsConfiguration {

    @Bean
    public DeadLetterMetrics deadLetterMetrics(MeterRegistry meterRegistry) {
        return new DeadLetterMetrics(meterRegistry);
    }
}
