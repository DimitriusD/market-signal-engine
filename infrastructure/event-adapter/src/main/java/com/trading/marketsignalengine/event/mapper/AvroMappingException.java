package com.trading.marketsignalengine.event.mapper;

public final class AvroMappingException extends RuntimeException {

    public AvroMappingException(String message) {
        super(message);
    }

    public AvroMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
