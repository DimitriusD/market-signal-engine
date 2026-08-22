package com.trading.marketsignalengine.application.domain.model.feature;

import java.time.Instant;
import lombok.Builder;

/**
 * Lineage of the upstream inputs MFS v2 computed this snapshot from: which order-book state
 * (timestamp, sequence, exchange update id, event id, processed time) and which last trade. Carried
 * verbatim so a signal can be traced back past the feature snapshot to the raw market state.
 */
@Builder(toBuilder = true)
public record FeatureSourceState(
        Instant sourceOrderBookStateTs,
        Long sourceOrderBookStateSeq,
        Long sourceOrderBookExchangeUpdateId,
        String sourceOrderBookStateEventId,
        Instant sourceOrderBookProcessedTs,
        Instant sourceTradeTs,
        int publishedDepth) {
}
