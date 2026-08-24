package com.trading.marketsignalengine.application.service;

import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import com.trading.marketsignalengine.application.port.input.MarketFeaturesHandler;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationPublication;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationSnapshotPublisherPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Live V2 handle path: {@code receivedAt = clock.instant()} →
 * {@link ValidatedMarketInterpretationEvaluator} (validate → quality → assemble, with
 * {@code receivedAt} as the explicit quality {@code assessedAt}) → publish the snapshot with its
 * transport timestamps. The only wall-clock reads of the live flow happen here, through the injected
 * clock; replay feeds the same evaluator a recorded instant instead.
 *
 * <p>Nothing is caught or hidden: validation failures propagate (the transport adapter routes them
 * to the DLT), evaluation failures propagate, publish failures propagate so the transport can retry
 * / recover with bounded behaviour. A {@code null} output never reaches the publisher — the
 * evaluator fails fast before that.
 */
public class MarketInterpretationHandleService implements MarketFeaturesHandler {

    private final ValidatedMarketInterpretationEvaluator evaluator;
    private final MarketInterpretationSnapshotPublisherPort publisher;
    private final Clock clock;

    public MarketInterpretationHandleService(ValidatedMarketInterpretationEvaluator evaluator,
                                             MarketInterpretationSnapshotPublisherPort publisher,
                                             Clock clock) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void handle(MarketFeaturesSnapshot features) {
        Instant receivedAt = clock.instant();
        MarketInterpretationSnapshot snapshot = evaluator.evaluate(features, receivedAt);
        Instant processedAt = clock.instant();
        publisher.publish(new MarketInterpretationPublication(snapshot, receivedAt, processedAt));
    }
}
