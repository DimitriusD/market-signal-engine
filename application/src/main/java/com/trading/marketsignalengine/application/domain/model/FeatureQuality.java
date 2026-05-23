package com.trading.marketsignalengine.application.domain.model;

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
