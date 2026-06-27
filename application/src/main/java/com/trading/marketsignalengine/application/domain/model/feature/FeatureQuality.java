package com.trading.marketsignalengine.application.domain.model.feature;

import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import lombok.Builder;

@Builder
public record FeatureQuality(
        SyncStatus syncStatus,
        boolean staleOrderBookState,
        boolean staleTrades,
        boolean incompleteBook,
        Long orderBookStateAgeMs,
        Long tradeAgeMs) {

    public boolean isTradable() {
        return syncStatus == SyncStatus.IN_SYNC
                && !staleOrderBookState
                && !staleTrades
                && !incompleteBook;
    }
}
