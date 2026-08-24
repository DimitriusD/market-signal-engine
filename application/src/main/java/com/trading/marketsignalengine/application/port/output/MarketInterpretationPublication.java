package com.trading.marketsignalengine.application.port.output;

import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.require;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requireNonNull;
import static com.trading.marketsignalengine.application.domain.interpretation.Invariants.requirePositiveInstant;

import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import java.time.Instant;

/**
 * One interpretation snapshot together with the transport timestamps of its live handling: the
 * domain {@link MarketInterpretationSnapshot} deliberately carries no transport time (its
 * {@code evaluatedAt} is the upstream market evaluation tick and its id is transport-independent),
 * but the output contract's {@code MetadataEvent} requires {@code receivedTs} and
 * {@code processedTs}. This immutable application value pairs the two without touching the snapshot:
 * {@code receivedAt} is the instant the feature snapshot entered the live handler (also used as the
 * explicit quality {@code assessedAt}), {@code processedAt} the instant assembly finished before the
 * publish call. Neither participates in the deterministic snapshot id and neither ever replaces
 * {@code evaluatedAt}.
 */
public record MarketInterpretationPublication(
        MarketInterpretationSnapshot snapshot,
        Instant receivedAt,
        Instant processedAt) {

    public MarketInterpretationPublication {
        requireNonNull(snapshot, "publication.snapshot");
        requirePositiveInstant(receivedAt, "publication.receivedAt");
        requirePositiveInstant(processedAt, "publication.processedAt");
        require(!processedAt.isBefore(receivedAt),
                "publication.processedAt " + processedAt + " must not be before receivedAt " + receivedAt);
    }
}
