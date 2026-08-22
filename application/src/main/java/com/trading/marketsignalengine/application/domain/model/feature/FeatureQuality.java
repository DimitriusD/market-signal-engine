package com.trading.marketsignalengine.application.domain.model.feature;

import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import java.util.List;
import lombok.Builder;

/**
 * Feature quality as published by MFS v2. The legacy per-source flags ({@code syncStatus},
 * {@code staleOrderBookState}, {@code staleTrades}, {@code incompleteBook}) are what the quality gate
 * reads today; the aggregate {@code status} / {@code qualityReasons} (plus {@code warmingUp},
 * {@code futureEventDetected}) are carried from MFS v2 and become a gate input in Блок 2 п. 2.4.
 * {@code status == null} means the upstream writer predates the field and must be treated as unknown
 * (fail closed), never as OK.
 */
@Builder(toBuilder = true)
public record FeatureQuality(
        SyncStatus syncStatus,
        boolean staleOrderBookState,
        boolean staleTrades,
        boolean incompleteBook,
        Long orderBookStateAgeMs,
        Long tradeAgeMs,
        boolean sourceOrderBookTrusted,
        String sourceOrderBookReason,
        FeatureQualityStatus status,
        List<String> qualityReasons,
        boolean futureEventDetected,
        boolean warmingUp) {

    public FeatureQuality {
        qualityReasons = qualityReasons == null ? List.of() : List.copyOf(qualityReasons);
    }

    public boolean isTradable() {
        return syncStatus == SyncStatus.IN_SYNC
                && !staleOrderBookState
                && !staleTrades
                && !incompleteBook;
    }
}
