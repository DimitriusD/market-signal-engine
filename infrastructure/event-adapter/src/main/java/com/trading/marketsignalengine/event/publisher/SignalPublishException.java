package com.trading.marketsignalengine.event.publisher;

/** Bounded publish failure (timeout, broker error, interruption). Retryable by the listener error handler. */
public class SignalPublishException extends RuntimeException {

    public SignalPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
