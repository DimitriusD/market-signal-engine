package com.trading.marketsignalengine.application.domain.model.feature;

import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import lombok.Builder;

@Builder
public record FeatureQuality(
        SyncStatus syncStatus,
        boolean staleBbo,
        boolean staleBook,
        boolean staleTrades,
        boolean incompleteBook,
        Long bboAgeMs,
        Long bookAgeMs,
        Long tradeAgeMs) {

    public boolean isTradable() {
        return syncStatus == SyncStatus.IN_SYNC
                && !staleBbo
                && !staleBook
                && !staleTrades
                && !incompleteBook;
    }
}
