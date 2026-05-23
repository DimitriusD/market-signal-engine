package com.trading.marketsignalengine.application.domain.model;

public enum SyncStatus {
    IN_SYNC,
    RECOVERING,
    OUT_OF_SYNC,
    STALE,
    UNKNOWN
}
