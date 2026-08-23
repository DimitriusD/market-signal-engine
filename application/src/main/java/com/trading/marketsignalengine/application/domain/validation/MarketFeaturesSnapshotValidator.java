package com.trading.marketsignalengine.application.domain.validation;

import com.trading.marketsignalengine.application.domain.model.SyncStatus;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureDiagnostics;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQuality;
import com.trading.marketsignalengine.application.domain.model.feature.FeatureQualityStatus;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Structural / contract guard on the inbound MFS v2 feature snapshot. It rejects <em>contract
 * contradictions</em> — missing identity or lineage, unsupported feature-set / schema version,
 * impossible timestamps, a trigger the engine does not know, an aggregate quality status that
 * contradicts its own flags — with {@link InvalidMarketFeaturesSnapshotException}, which the transport
 * adapter treats as non-retryable (straight to the DLT).
 *
 * <p>It deliberately does <b>not</b> reject a valid but bad market state: {@code DEGRADED},
 * {@code UNSAFE}, {@code NO_DATA}, warm-up, staleness, {@code futureEventDetected} or a failed
 * calculator are legitimate events the producer emits on purpose. They pass validation unchanged and
 * reach the quality gate, which turns them into a {@code NO_TRADE} signal snapshot. In one line:
 * contract inconsistency → exception / DLT; bad market quality → valid input → no-trade output.
 *
 * <p>The quality invariants mirror the producer's {@code FeatureQualityCalculator#aggregate}
 * (market-feature-service): {@code NO_DATA} ⇔ no book and no trades ({@code NO_MARKET_DATA});
 * {@code UNSAFE} ⇔ missing / untrusted / out-of-sync / hard-stale book; {@code DEGRADED} ⇔ any softer
 * impairment (soft staleness, incomplete depth, warm-up, future event, trade-history gap, failed
 * calculator); {@code OK} ⇔ none of the above. Only the minimal, producer-guaranteed implications are
 * enforced so that a future producer change that adds causes cannot be mistaken for corruption.
 *
 * <p>Compatibility: {@code featureSetVersion} must be in the configured allowlist and the Avro
 * {@code schemaVersion} must be one this engine was written against (MFS v2 publishes {@code 1}). An
 * unknown version is rejected (fail closed) instead of being interpreted on assumptions; widening the
 * allowlist is a deliberate configuration change, visible in deployment config.
 */
public class MarketFeaturesSnapshotValidator {

    /** Avro envelope versions this engine understands for every allowlisted feature set (MFS v2 → 1). */
    public static final Set<Integer> SUPPORTED_SCHEMA_VERSIONS = Set.of(1);

    public static final String TRIGGER_ORDER_BOOK_L2_SNAPSHOT = "ORDER_BOOK_L2_SNAPSHOT";
    public static final String TRIGGER_TRADE = "TRADE";
    public static final String TRIGGER_TIMER = "TIMER";
    /** Trigger sources MFS v2 emits; {@code UNKNOWN} and anything else is a contract error. */
    public static final Set<String> SUPPORTED_TRIGGER_SOURCES =
            Set.of(TRIGGER_ORDER_BOOK_L2_SNAPSHOT, TRIGGER_TRADE, TRIGGER_TIMER);

    /** Feature-group ids MFS v2 reports in {@code diagnostics.failedFeatureGroups}. */
    public static final Set<String> KNOWN_FEATURE_GROUPS = Set.of("bbo", "order-book", "trade-flow", "short-term-regime");

    public static final String REASON_NO_MARKET_DATA = "NO_MARKET_DATA";
    /** Producer reasons that make a snapshot {@code UNSAFE}. */
    public static final Set<String> UNSAFE_REASONS =
            Set.of("NO_ORDER_BOOK", "BOOK_UNTRUSTED", "BOOK_OUT_OF_SYNC", "STALE_ORDER_BOOK_HARD");
    /** Producer reasons that make a snapshot {@code DEGRADED}. */
    public static final Set<String> DEGRADED_REASONS = Set.of(
            "STALE_ORDER_BOOK", "STALE_TRADES", "INCOMPLETE_BOOK", "WARMING_UP",
            "FUTURE_EVENT", "TRADE_HISTORY_GAP", "CALCULATOR_FAILURE");

    private final Set<String> supportedFeatureSetVersions;

    public MarketFeaturesSnapshotValidator(Set<String> supportedFeatureSetVersions) {
        if (supportedFeatureSetVersions == null || supportedFeatureSetVersions.isEmpty()) {
            throw new IllegalArgumentException("supportedFeatureSetVersions must not be empty");
        }
        Set<String> normalized = new TreeSet<>();
        for (String version : supportedFeatureSetVersions) {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("supportedFeatureSetVersions must not contain blank entries");
            }
            normalized.add(version.trim());
        }
        this.supportedFeatureSetVersions = Set.copyOf(normalized);
    }

    public Set<String> supportedFeatureSetVersions() {
        return supportedFeatureSetVersions;
    }

    public void validate(MarketFeaturesSnapshot snapshot) {
        if (snapshot == null) {
            throw new InvalidMarketFeaturesSnapshotException("MarketFeaturesSnapshot must not be null");
        }
        validateIdentityAndLineage(snapshot);
        validateCompatibility(snapshot);
        validateTimestampsAndTrigger(snapshot);
        validateQuality(snapshot);
        validateDiagnostics(snapshot.diagnostics());
    }

    // ------------------------------------------------------------------ identity / lineage

    private static void validateIdentityAndLineage(MarketFeaturesSnapshot snapshot) {
        requireNonBlank(snapshot.snapshotId(), "snapshotId");
        requireNonBlank(snapshot.instrumentId(), "instrumentId");
        requireNonBlank(snapshot.featureSetVersion(), "featureSetVersion");
        requireNonBlank(snapshot.configHash(), "configHash");
    }

    // ------------------------------------------------------------------ compatibility

    private void validateCompatibility(MarketFeaturesSnapshot snapshot) {
        if (!supportedFeatureSetVersions.contains(snapshot.featureSetVersion())) {
            throw new InvalidMarketFeaturesSnapshotException(
                    "unsupported featureSetVersion '" + snapshot.featureSetVersion()
                            + "' (supported: " + new TreeSet<>(supportedFeatureSetVersions) + ")");
        }
        Integer schemaVersion = snapshot.schemaVersion();
        if (schemaVersion == null) {
            throw new InvalidMarketFeaturesSnapshotException(
                    "schemaVersion must not be null for featureSetVersion '" + snapshot.featureSetVersion() + "'");
        }
        if (!SUPPORTED_SCHEMA_VERSIONS.contains(schemaVersion)) {
            throw new InvalidMarketFeaturesSnapshotException(
                    "unsupported schemaVersion " + schemaVersion + " for featureSetVersion '"
                            + snapshot.featureSetVersion() + "' (supported: " + new TreeSet<>(SUPPORTED_SCHEMA_VERSIONS) + ")");
        }
    }

    // ------------------------------------------------------------------ timestamps / trigger

    /**
     * MFS v2 timing contract: {@code evaluationTs} is the as-of instant every window was selected
     * with. For a market-event trigger ({@code TRADE}, {@code ORDER_BOOK_L2_SNAPSHOT}) it <em>is</em>
     * the trigger's exchange timestamp, i.e. {@code metadata.exchangeTs} ({@code eventTime}). For a
     * {@code TIMER} tick there is no market event: {@code eventTime} is epoch zero and the as-of
     * instant is the processing instant, so it cannot be after {@code computedAt}. A market-event
     * as-of instant <em>after</em> {@code computedAt} is not a contract error by itself (clock skew
     * upstream) — but then the producer must have reported it via {@code quality.futureEventDetected}.
     */
    private static void validateTimestampsAndTrigger(MarketFeaturesSnapshot snapshot) {
        Instant evaluationTs = requirePositive(snapshot.evaluationTs(), "evaluationTs");
        Instant computedAt = requirePositive(snapshot.computedAt(), "computedAt");
        Instant eventTime = snapshot.eventTime();
        if (eventTime == null) {
            throw new InvalidMarketFeaturesSnapshotException("eventTime must not be null");
        }

        String trigger = snapshot.triggerSource();
        requireNonBlank(trigger, "triggerSource");
        if (!SUPPORTED_TRIGGER_SOURCES.contains(trigger)) {
            throw new InvalidMarketFeaturesSnapshotException(
                    "unsupported triggerSource '" + trigger + "' (supported: " + new TreeSet<>(SUPPORTED_TRIGGER_SOURCES) + ")");
        }

        if (TRIGGER_TIMER.equals(trigger)) {
            if (eventTime.toEpochMilli() < 0L) {
                throw new InvalidMarketFeaturesSnapshotException("eventTime must not be negative for TIMER trigger");
            }
            if (evaluationTs.isAfter(computedAt)) {
                throw new InvalidMarketFeaturesSnapshotException(
                        "TIMER evaluationTs " + evaluationTs + " must not be after computedAt " + computedAt
                                + " (a clock tick has no future source event)");
            }
            return;
        }

        if (eventTime.toEpochMilli() <= 0L) {
            throw new InvalidMarketFeaturesSnapshotException(
                    "eventTime must be positive for " + trigger + " trigger, got " + eventTime);
        }
        if (!evaluationTs.equals(eventTime)) {
            throw new InvalidMarketFeaturesSnapshotException(
                    trigger + " evaluationTs " + evaluationTs + " must equal the source event time " + eventTime
                            + " (MFS v2 evaluates as-of the trigger's exchangeTs)");
        }
    }

    // ------------------------------------------------------------------ quality

    private static void validateQuality(MarketFeaturesSnapshot snapshot) {
        FeatureQuality quality = snapshot.quality();
        if (quality == null) {
            throw new InvalidMarketFeaturesSnapshotException("quality must not be null for " + snapshot.featureSetVersion());
        }
        FeatureQualityStatus status = quality.status();
        if (status == null) {
            throw new InvalidMarketFeaturesSnapshotException("quality.status must not be null for " + snapshot.featureSetVersion());
        }

        // Future source timestamp must be reported honestly: for a market-event trigger the as-of
        // instant is the trigger's own exchangeTs, so evaluationTs > computedAt means the trigger was
        // ahead of the producer clock — exactly the condition the producer flags as futureEventDetected.
        if (!TRIGGER_TIMER.equals(snapshot.triggerSource())
                && snapshot.evaluationTs().isAfter(snapshot.computedAt())
                && !quality.futureEventDetected()) {
            throw new InvalidMarketFeaturesSnapshotException(
                    "evaluationTs " + snapshot.evaluationTs() + " is after computedAt " + snapshot.computedAt()
                            + " but quality.futureEventDetected=false");
        }

        List<String> reasons = quality.qualityReasons();
        FeatureDiagnostics diagnostics = snapshot.diagnostics();
        boolean calculatorFailure = diagnostics != null && diagnostics.hasFailures();

        switch (status) {
            case OK -> requireCleanForOk(quality, reasons, calculatorFailure);
            case DEGRADED -> {
                boolean degradedCause = quality.staleOrderBookState()
                        || quality.staleTrades()
                        || quality.incompleteBook()
                        || quality.warmingUp()
                        || quality.futureEventDetected()
                        || calculatorFailure
                        || containsAny(reasons, DEGRADED_REASONS);
                if (!degradedCause) {
                    throw new InvalidMarketFeaturesSnapshotException(
                            "quality.status=DEGRADED without any degraded cause (flags, diagnostics or qualityReasons "
                                    + DEGRADED_REASONS + "); reasons=" + reasons);
                }
            }
            case UNSAFE -> {
                boolean unsafeCause = !quality.sourceOrderBookTrusted()
                        || quality.syncStatus() != SyncStatus.IN_SYNC
                        || containsAny(reasons, UNSAFE_REASONS);
                if (!unsafeCause) {
                    throw new InvalidMarketFeaturesSnapshotException(
                            "quality.status=UNSAFE without an unsafe cause (untrusted book, non-IN_SYNC book or reasons "
                                    + UNSAFE_REASONS + "); reasons=" + reasons);
                }
            }
            case NO_DATA -> {
                if (!reasons.contains(REASON_NO_MARKET_DATA)) {
                    throw new InvalidMarketFeaturesSnapshotException(
                            "quality.status=NO_DATA requires qualityReasons to contain " + REASON_NO_MARKET_DATA
                                    + "; reasons=" + reasons);
                }
            }
        }
    }

    /** {@code OK} means "nothing impaired": every flag clean, no calculator failure, no reason. */
    private static void requireCleanForOk(FeatureQuality quality, List<String> reasons, boolean calculatorFailure) {
        requireForOk(quality.sourceOrderBookTrusted(), "sourceOrderBookTrusted=false");
        requireForOk(quality.syncStatus() == SyncStatus.IN_SYNC, "syncStatus=" + quality.syncStatus());
        requireForOk(!quality.staleOrderBookState(), "staleOrderBookState=true");
        requireForOk(!quality.staleTrades(), "staleTrades=true");
        requireForOk(!quality.incompleteBook(), "incompleteBook=true");
        requireForOk(!quality.warmingUp(), "warmingUp=true");
        requireForOk(!quality.futureEventDetected(), "futureEventDetected=true");
        requireForOk(!calculatorFailure, "diagnostics.failedFeatureGroups is not empty");
        requireForOk(reasons.isEmpty(), "qualityReasons=" + reasons);
    }

    private static void requireForOk(boolean condition, String contradiction) {
        if (!condition) {
            throw new InvalidMarketFeaturesSnapshotException("quality.status=OK contradicts " + contradiction);
        }
    }

    // ------------------------------------------------------------------ diagnostics

    private static void validateDiagnostics(FeatureDiagnostics diagnostics) {
        if (diagnostics == null) {
            return;
        }
        for (String group : diagnostics.failedFeatureGroups()) {
            if (group == null || group.isBlank()) {
                throw new InvalidMarketFeaturesSnapshotException("diagnostics.failedFeatureGroups must not contain blank ids");
            }
        }
        if (diagnostics.totalFeatureGroups() < 0) {
            throw new InvalidMarketFeaturesSnapshotException("diagnostics.totalFeatureGroups must not be negative");
        }
        if (diagnostics.totalFeatureGroups() > 0
                && diagnostics.failedFeatureGroups().size() > diagnostics.totalFeatureGroups()) {
            throw new InvalidMarketFeaturesSnapshotException(
                    "diagnostics reports " + diagnostics.failedFeatureGroups().size() + " failed groups out of "
                            + diagnostics.totalFeatureGroups());
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean containsAny(List<String> reasons, Set<String> candidates) {
        for (String reason : reasons) {
            if (candidates.contains(reason)) {
                return true;
            }
        }
        return false;
    }

    private static Instant requirePositive(Instant value, String fieldName) {
        if (value == null) {
            throw new InvalidMarketFeaturesSnapshotException(fieldName + " must not be null");
        }
        if (value.toEpochMilli() <= 0L) {
            throw new InvalidMarketFeaturesSnapshotException(fieldName + " must be a positive epoch timestamp, got " + value);
        }
        return value;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidMarketFeaturesSnapshotException(fieldName + " must not be blank");
        }
    }
}
