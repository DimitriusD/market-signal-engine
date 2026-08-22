package com.trading.marketsignalengine.config;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.signal")
public class SignalProperties {

    private String signalSetVersion;
    private BigDecimal maxSpreadBps;
    private BigDecimal buyFlowImbalance5sThreshold;
    private BigDecimal sellFlowImbalance5sThreshold;
    private Integer minTradeCount5sForTradeFlowSignal;
    private BigDecimal buyBookImbalanceThreshold;
    private BigDecimal sellBookImbalanceThreshold;
    private BigDecimal maxRealizedVolatilityBps1s;
    private Long microstructureSetupTtlMs;
    private Long riskOffTtlMs;
    private Long neutralTtlMs;
    /** Allowlist of upstream {@code featureSetVersion} values; anything else fails closed to the DLT. */
    private List<String> supportedFeatureSetVersions;
}
