package com.trading.marketsignalengine.event.publisher;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.port.output.MarketSignalSnapshotPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Temporary publisher until {@code MarketSignalSnapshotEvent} is available in trading-schemas.
 * TODO replace with KafkaMarketSignalSnapshotPublisher once MarketSignalSnapshotEvent is available in trading-schemas
 */
@Slf4j
@Component
public class LoggingMarketSignalSnapshotPublisher implements MarketSignalSnapshotPublisherPort {

    @Override
    public void publish(MarketSignalSnapshot snapshot) {
        log.info(
                "Generated market signal snapshot (logging-only): signalSnapshotId={}, sourceFeatureSnapshotId={}, "
                        + "exchange={}, symbol={}, instrumentId={}, marketBias={}, riskLevel={}, signalCount={}",
                snapshot.signalSnapshotId(),
                snapshot.sourceFeatureSnapshotId(),
                snapshot.exchange(),
                snapshot.symbol(),
                snapshot.instrumentId(),
                snapshot.marketBias(),
                snapshot.riskLevel(),
                snapshot.signals().size());
        log.debug("Market signal snapshot details: {}", snapshot);
    }
}
