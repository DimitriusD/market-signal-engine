package com.trading.marketsignalengine.application.port.output;

/**
 * Output port of the live V2 pipeline: publishes one assembled interpretation snapshot with its
 * transport timestamps. Implementations must be bounded (never block indefinitely) and must throw on
 * failure instead of swallowing it — the transport layer decides on retry / dead-lettering.
 */
public interface MarketInterpretationSnapshotPublisherPort {

    void publish(MarketInterpretationPublication publication);
}
