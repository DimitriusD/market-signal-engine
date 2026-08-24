package com.trading.marketsignalengine.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trading.marketsignalengine.application.domain.interpretation.MarketInterpretationSnapshot;
import com.trading.marketsignalengine.application.domain.interpretation.OpportunityStatus;
import com.trading.marketsignalengine.application.domain.interpretation.opportunity.OpportunityReasonCodes;
import com.trading.marketsignalengine.application.domain.validation.InvalidMarketFeaturesSnapshotException;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationPublication;
import com.trading.marketsignalengine.application.port.output.MarketInterpretationSnapshotPublisherPort;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The live V2 handle path: the injected clock provides {@code receivedAt} (= the explicit quality
 * {@code assessedAt}) and {@code processedAt}, the publisher is called exactly once with the
 * assembled snapshot, and no exception is ever swallowed — validation, evaluation and publish
 * failures all reach the caller (the Kafka error handler in production).
 */
class MarketInterpretationHandleServiceTest {

    /** Recording publisher: keeps every publication, optionally failing on publish. */
    private static final class RecordingPublisher implements MarketInterpretationSnapshotPublisherPort {
        final List<MarketInterpretationPublication> published = new ArrayList<>();
        RuntimeException failure;

        @Override
        public void publish(MarketInterpretationPublication publication) {
            if (failure != null) {
                throw failure;
            }
            published.add(publication);
        }
    }

    private final RecordingPublisher publisher = new RecordingPublisher();
    private final Clock clock = Clock.fixed(RuntimeFixtures.ASSESSED_AT, ZoneOffset.UTC);
    private final MarketInterpretationHandleService service =
            new MarketInterpretationHandleService(RuntimeFixtures.evaluator(), publisher, clock);

    @Test
    void publishesExactlyOnceWithClockDerivedTransportTimestamps() {
        service.handle(RuntimeFixtures.bullishSnapshot());

        assertEquals(1, publisher.published.size(), "publisher must be called exactly once");
        MarketInterpretationPublication publication = publisher.published.getFirst();
        assertEquals(RuntimeFixtures.ASSESSED_AT, publication.receivedAt(), "receivedAt comes from the clock");
        assertEquals(RuntimeFixtures.ASSESSED_AT, publication.processedAt());
        // the clock instant is also the quality assessedAt: the snapshot equals a replay at that instant
        MarketInterpretationSnapshot expected =
                RuntimeFixtures.evaluator().evaluate(RuntimeFixtures.bullishSnapshot(), RuntimeFixtures.ASSESSED_AT);
        assertEquals(expected, publication.snapshot());
        // evaluatedAt stays the upstream market tick, never the processing time
        assertEquals(RuntimeFixtures.EVENT_TIME, publication.snapshot().evaluatedAt());
        assertEquals(OpportunityStatus.CANDIDATE, publication.snapshot().marketOpportunity().status());
    }

    @Test
    void expiredCandidateIsPublishedAsNoOpportunity() {
        // fixture candidate deadline is EVENT_TIME+400 (exclusive); the clock reads exactly that
        Clock late = Clock.fixed(RuntimeFixtures.EVENT_TIME.plusMillis(400), ZoneOffset.UTC);
        MarketInterpretationHandleService lateService =
                new MarketInterpretationHandleService(RuntimeFixtures.evaluator(), publisher, late);

        lateService.handle(RuntimeFixtures.bullishSnapshot());

        MarketInterpretationSnapshot snapshot = publisher.published.getFirst().snapshot();
        assertEquals(OpportunityStatus.NO_OPPORTUNITY, snapshot.marketOpportunity().status());
        assertTrue(snapshot.marketOpportunity().reasonCodes()
                .contains(OpportunityReasonCodes.OPPORTUNITY_NO_OPPORTUNITY));
    }

    @Test
    void nonEligibleQualityIsPublishedAsBlocked() {
        service.handle(RuntimeFixtures.unsafeSnapshot());

        assertEquals(OpportunityStatus.BLOCKED,
                publisher.published.getFirst().snapshot().marketOpportunity().status());
    }

    @Test
    void validationFailurePropagatesAndNothingIsPublished() {
        assertThrows(InvalidMarketFeaturesSnapshotException.class,
                () -> service.handle(RuntimeFixtures.invalidSnapshot()));
        assertThrows(InvalidMarketFeaturesSnapshotException.class, () -> service.handle(null));
        assertTrue(publisher.published.isEmpty(), "an invalid input must never reach the publisher");
    }

    @Test
    void publishFailurePropagatesUnchanged() {
        publisher.failure = new IllegalStateException("simulated publish failure");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.handle(RuntimeFixtures.bullishSnapshot()));

        assertEquals("simulated publish failure", ex.getMessage());
    }

    @Test
    void rejectsNullDependencies() {
        assertThrows(NullPointerException.class,
                () -> new MarketInterpretationHandleService(null, publisher, clock));
        assertThrows(NullPointerException.class,
                () -> new MarketInterpretationHandleService(RuntimeFixtures.evaluator(), null, clock));
        assertThrows(NullPointerException.class,
                () -> new MarketInterpretationHandleService(RuntimeFixtures.evaluator(), publisher, null));
    }
}
