package com.trading.marketsignalengine.application.domain.validation;

public class InvalidMarketFeaturesSnapshotException extends RuntimeException {

    public InvalidMarketFeaturesSnapshotException(String message) {
        super(message);
    }
}
