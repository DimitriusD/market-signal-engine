package com.trading.marketsignalengine.application.port.output;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;

public interface MarketSignalSnapshotPublisherPort {

    void publish(MarketSignalSnapshot snapshot);
}
