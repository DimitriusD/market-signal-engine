# market-signal-engine

Event-driven Kafka Avro service that consumes market feature snapshots and produces interpreted market signal snapshots.

## What it does

`market-signal-engine` reads normalized market feature snapshots from Kafka, evaluates explainable signal rules in a pure domain/application core, and publishes market signal snapshots to Kafka.

## Input

| Property | Value |
|----------|-------|
| Topic | `market.feature.snapshot.v1` |
| Key | `instrumentId` |
| Value | `com.trading.contracts.feature.MarketFeaturesSnapshotEvent` |

## Output

| Property | Value |
|----------|-------|
| Topic | `state.market.signals.v1` |
| Key | `instrumentId` |
| Value | `com.trading.contracts.signal.MarketSignalSnapshotEvent` |

## Architecture

```
market-signal-engine
    ├── application              # domain models, rules, handler, ports (no Kafka/Avro/Spring)
    └── infrastructure
        ├── app                  # Spring Boot composition root
        └── event-adapter        # Kafka Avro consumer, mappers, publisher adapter
```

Runtime flow:

```
market.feature.snapshot.v1
    ↓
MarketFeaturesKafkaConsumer
    ↓
MarketFeaturesSnapshotAvroMapper
    ↓
domain MarketFeaturesSnapshot
    ↓
MarketFeaturesHandler (MarketSignalHandleService, evaluatedAt = now(clock))
    ↓
ValidatedMarketSignalEvaluator  ←  shared with ReplayHarness
    ├─ MarketFeaturesSnapshotValidator (MFS v2 contract → DLT on contradiction)
    └─ DefaultMarketSignalEngine (StandardSignalEngine wiring)
    ↓
domain MarketSignalSnapshot
    ↓
MarketSignalSnapshotPublisher
    ↓
state.market.signals.v1
```

## Local setup

Publish external schema dependencies to Maven Local:

```bash
cd ../trading-schemas
./gradlew clean publishToMavenLocal

cd ../trading-common
./gradlew clean publishToMavenLocal
```

Start Kafka infrastructure and run the service:

```bash
cd ../market-signal-engine
docker compose up -d
./gradlew clean build
./gradlew :infrastructure:app:bootRun
```

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `APP_SCHEMA_REGISTRY_URL` | `http://localhost:8081` | Confluent Schema Registry URL |
| `APP_KAFKA_CONSUMER_GROUP_ID` | `market-signal-engine` | Kafka consumer group id |
| `APP_KAFKA_TOPIC_MARKET_FEATURES` | `market.feature.snapshot.v1` | Input topic for feature snapshots |
| `APP_KAFKA_TOPIC_MARKET_SIGNALS` | `state.market.signals.v1` | Output topic for signal snapshots |
| `APP_SIGNAL_SET_VERSION` | `mse-signals-v8` | Signal set version label |
| `APP_SIGNAL_MAX_SPREAD_BPS` | `2.0` | Max acceptable spread in bps |
| `APP_SIGNAL_BUY_FLOW_IMBALANCE_5S_THRESHOLD` | `0.15` | Buy pressure threshold for signedFlowImbalance5s |
| `APP_SIGNAL_SELL_FLOW_IMBALANCE_5S_THRESHOLD` | `-0.15` | Sell pressure threshold for signedFlowImbalance5s |
| `APP_SIGNAL_MIN_TRADE_COUNT_5S_FOR_TRADE_FLOW_SIGNAL` | `10` | Min 5s trade count before a trade-flow signal |
| `APP_SIGNAL_BUY_BOOK_IMBALANCE_THRESHOLD` | `0.60` | Bullish top5Imbalance threshold |
| `APP_SIGNAL_SELL_BOOK_IMBALANCE_THRESHOLD` | `-0.60` | Bearish top5Imbalance threshold |
| `APP_SIGNAL_MAX_REALIZED_VOLATILITY_BPS_1S` | `50.0` | Max acceptable 1s realized volatility (log-return bps); uncalibrated placeholder, see `docs/path-to-paper-trading.md` §8.2 |
| `APP_SIGNAL_MICROSTRUCTURE_SETUP_TTL_MS` | `2000` | TTL of a directional microstructure setup snapshot |
| `APP_SIGNAL_RISK_OFF_TTL_MS` | `5000` | TTL of a risk-off snapshot |
| `APP_SIGNAL_NEUTRAL_TTL_MS` | `1000` | TTL of a neutral snapshot |
| `APP_SIGNAL_SUPPORTED_FEATURE_SET_VERSIONS` | `mfs-features-v2` | Comma-separated allowlist of upstream `featureSetVersion`; any other version fails closed to the DLT |
| `APP_KAFKA_PUBLISH_TIMEOUT_MS` | `6500` | Max wait on the consumer thread for the output broker ack. Must be **strictly greater** than `delivery.timeout.ms` (validated at startup). On timeout the wait is abandoned and the input goes through retry → DLT |
| `APP_KAFKA_PRODUCER_ACKS` / `_REQUEST_TIMEOUT_MS` / `_DELIVERY_TIMEOUT_MS` | `all` / `3000` / `5000` | Output producer durability and in-producer time bounds; `acks=all` and `enable.idempotence=true` are also forced in code. Hierarchy `request < delivery < publish` is validated at startup |
| `APP_KAFKA_RETRY_BACKOFF_MS` / `APP_KAFKA_RETRY_MAX_ATTEMPTS` | `1000` / `3` | Listener retry policy before dead-lettering to `<input-topic>.DLT`. `max-attempts` = retries **after** the first delivery (`3` → 4 deliveries / publisher attempts). Mapping/contract failures are not retried |
| `APP_KAFKA_LISTENER_CONCURRENCY` / `_ACK_MODE` / `_POLL_TIMEOUT_MS` / `_AUTO_STARTUP` | `1` / `batch` / `1000` / `true` | `spring.kafka.listener.*`, applied through Boot's container-factory configurer |
| `APP_KAFKA_CONSUMER_AUTO_OFFSET_RESET` | `latest` | Offset reset for a new consumer group |

## Live / replay parity: one validated evaluator

Both the live Kafka path and the in-process replay harness run the **same** application component,
`ValidatedMarketSignalEvaluator` (`application/.../service`):

```
validate(MarketFeaturesSnapshot)  →  MarketSignalEngine.evaluate(features, evaluatedAt)
```

- Live (`MarketSignalHandleService`): `evaluatedAt = Instant.now(clock)` from the injected `Clock`
  (no hidden wall-clock read inside the application flow) → evaluator → publisher → metrics.
- Replay (`ReplayHarness`): `evaluatedAt` comes from an explicit resolver
  (`Function<MarketFeaturesSnapshot, Instant>`, default `computedAt` or `eventTime`, or `fixed(...)`)
  → the same evaluator. No publishing, no metrics, no Kafka/Spring.
- Invariant: same `MarketFeaturesSnapshot` + same `evaluatedAt` + same configuration ⇒ same
  `MarketSignalSnapshot` (same deterministic `signalSnapshotId`) or the same validation exception.
  Replay never bypasses `MarketFeaturesSnapshotValidator`; a snapshot rejected in Kafka is rejected
  identically in replay. `StandardSignalEngine` remains the single rule wiring — there is no second
  copy of the production rules for replay.
- Fail-fast: `null` input is a validation error, `null evaluatedAt` and a `null` engine result are
  programming errors (`IllegalArgumentException` / `IllegalStateException`); invalid input never
  reaches the engine or the publisher, engine failures are never swallowed, `null` is never published.

## MFS v2 input contract validation

`MarketFeaturesSnapshotValidator` rejects **contract contradictions** with
`InvalidMarketFeaturesSnapshotException` (non-retryable → `<input-topic>.DLT`). It does **not**
reject a valid but bad market state: `DEGRADED`, `UNSAFE`, `NO_DATA`, warm-up, staleness,
`futureEventDetected` or a failed calculator are legitimate producer outputs — they pass validation
and the quality gate turns them into a `NO_TRADE` signal snapshot (current policy: `DEGRADED` is a hard
block). Contract inconsistency → exception / DLT; bad market quality → valid input → no-trade output.

Checked invariants (mirroring `market-feature-service` `FeatureQualityCalculator`):

| Area | Rule |
|---|---|
| identity / lineage | `snapshotId`, `instrumentId`, `featureSetVersion`, `configHash` non-blank |
| compatibility | `featureSetVersion` ∈ allowlist (`APP_SIGNAL_SUPPORTED_FEATURE_SET_VERSIONS`); Avro `metadata.schemaVersion` = `1` (MFS v2) |
| timestamps | `evaluationTs` and `computedAt` non-null, positive; `eventTime` non-null |
| trigger | `triggerSource` ∈ {`ORDER_BOOK_L2_SNAPSHOT`, `TRADE`, `TIMER`}; `UNKNOWN`/anything else rejected |
| market-event trigger | `eventTime` positive and `evaluationTs == eventTime` (MFS evaluates as-of the trigger `exchangeTs`); `evaluationTs > computedAt` is allowed only with `quality.futureEventDetected = true` |
| `TIMER` trigger | `eventTime` may be epoch zero (no source market event); `evaluationTs ≤ computedAt` |
| quality presence | `quality` and `quality.status` non-null |
| `OK` | `sourceOrderBookTrusted`, `syncStatus = IN_SYNC`, no stale book/trades, no incomplete book, no warm-up, no future event, no failed calculator, empty `qualityReasons` |
| `DEGRADED` | at least one degraded cause: a flag (stale book/trades, incomplete book, warm-up, future event), `diagnostics.failedFeatureGroups`, or a degraded reason (`STALE_ORDER_BOOK`, `STALE_TRADES`, `INCOMPLETE_BOOK`, `WARMING_UP`, `FUTURE_EVENT`, `TRADE_HISTORY_GAP`, `CALCULATOR_FAILURE`) |
| `UNSAFE` | at least one unsafe cause: untrusted book, non-`IN_SYNC` book, or reason ∈ {`NO_ORDER_BOOK`, `BOOK_UNTRUSTED`, `BOOK_OUT_OF_SYNC`, `STALE_ORDER_BOOK_HARD`} |
| `NO_DATA` | `qualityReasons` contains `NO_MARKET_DATA` |
| `sourceOrderBookTrusted=false` | valid for `UNSAFE` / `NO_DATA`; contradicts only `OK` |
| diagnostics | `failedFeatureGroups` ids non-blank (MFS ids: `bbo`, `order-book`, `trade-flow`, `short-term-regime`); failed count ≤ total; a failure is never a neutral feature |

## Availability normalization (input side, not yet trading logic)

`FeatureAvailabilityResolver` (`application/.../domain/availability`) resolves the trade-flow
rolling windows `1S` / `5S` / `15S` / `60S` to `AVAILABLE | WARMING_UP | UNAVAILABLE | UNTRUSTED | FAILED`
(`TradeFlowAvailability`). It is pure, deterministic, immutable, and does **not** influence the
current V1 rules or golden outputs — it is the input foundation for the V2 multi-horizon stage.

- **null ≠ zero.** A real numeric zero (zero imbalance, zero volume, `tradeIntensity = 0`) is an
  `AVAILABLE` value; an absent value is one of the four non-available states.
- **Presence markers.** A window is *computed* when any nullable computed metric is non-null
  (`buy/sell/totalAggressiveVolume`, `signedTradeFlow`, `signedFlowImbalance`, `tradeIntensity`,
  `avgTradeSize`, `vwap`), or — `15S`/`60S` (nullable counters on the wire): any counter non-null
  (`0` = measured empty window, `null` = not covered); `1S`/`5S` (counters default to `0` on the
  wire): a *positive* counter. Known producer limitation: current MFS v2 emits `tradeIntensity = null`
  for a covered-but-empty 1S/5S window, so it is indistinguishable from an uncovered one and is
  reported conservatively as not computed.
- **Precedence (first match wins):** `FAILED` (`diagnostics.failedFeatureGroups` ∋ `trade-flow`, every
  horizon) → `UNTRUSTED` (`quality.staleTrades`, every horizon; `TRADE_HISTORY_GAP`, the uncovered
  horizons — MFS leaves exactly the windows spanning the gap uncovered) → `WARMING_UP` (not computed and
  `quality.warmingUp`) → `UNAVAILABLE` (not computed, no reason) → `AVAILABLE`.
- Global `warmingUp` does not make every horizon `WARMING_UP`: computed `1S`/`5S` stay `AVAILABLE`
  while `15S`/`60S` warm up. `sourceOrderBookTrusted` is book quality and never affects trade-flow.

## Market Interpretation V2 domain model (Stage 2, no evaluators yet)

`application/.../domain/interpretation` is the new canonical domain model of the engine — the internal
counterpart of `com.trading.contracts.signal.MarketInterpretationSnapshotEvent` (trading-schemas 1.1.0).
It is pure (no Avro/Kafka/Spring/`Clock`), immutable and typed, and **enforces the contract invariants
in constructors / factories / the aggregate**: exactly one `HorizonAssessment` per `MarketHorizon`
(`1S`, `5S`, `15S`, `60S`, stored in canonical order), `HorizonEligibility` (policy verdict) kept distinct
from input `FeatureAvailabilityStatus`, UNKNOWN never read as NEUTRAL, `EvidenceStrength` as a
`BigDecimal` in `[0,1]` (absence = absent, never `0`; not a probability), typed `ReasonCode`s,
`CrossHorizonAssessment` / `MarketOpportunity` consistency tables (`eligibleForTrading=false ⇔
opportunity BLOCKED`), full `FeatureLineage` + `InterpretationLineage`, and a deterministic
`interpretationSnapshotId` (`InterpretationSnapshotIdGenerator`, `mse-interpretation-id-v1`, derived
only from lineage). `MarketHorizon` is the single horizon type shared with the availability resolver.
Evaluators, the V2 Avro mapper/publisher/topic and Spring wiring are **not** implemented yet; V1
(`mse-signals-v8`) remains the runtime and regression baseline. Details: roadmap §15, "Етап 2".

## Quality assessment and per-horizon eligibility (Stage 3, pure, not yet wired)

`application/.../domain/interpretation/quality` turns a **validated** `MarketFeaturesSnapshot` plus an
explicit `assessedAt` and a `QualityEligibilityPolicy` (`maxFeatureAge`, `maxProcessingLatency`,
`blockFutureEvents`; no defaults, no production values yet) into a typed `QualityAssessment`:
`FeatureAvailabilityResolver → HorizonEligibilityResolver → TimingAssessmentResolver →
QualityAssessmentResolver`. Pure and deterministic (no Spring/Kafka/Avro/`Clock`/metrics): same
snapshot + `assessedAt` + policy ⇒ same result.

- **Exactly four `HorizonEligibility`** (`HorizonEligibilities`, canonical `1S, 5S, 15S, 60S`), each decided
  independently from trade-flow availability: `AVAILABLE → ELIGIBLE`, `WARMING_UP → WARMING_UP`,
  `UNAVAILABLE → UNAVAILABLE`, `UNTRUSTED → UNTRUSTED`, `FAILED → FAILED`; source `NO_DATA` → every horizon
  `UNAVAILABLE [SOURCE_NO_DATA]`. Computed `1S/5S` stay `ELIGIBLE` while `15S/60S` warm up or span a
  history gap; `null` never becomes ELIGIBLE / zero / NEUTRAL.
- **Timing** (`TimingAssessment`): `featureAgeMs = assessedAt − evaluationTs`,
  `processingLatencyMs = assessedAt − computedAt`; negative values are reported, never clamped, and mean
  `CLOCK_SKEW` (wins over `STALE`); thresholds are inclusive (`age <= max` fresh, `age > max` stale).
  `evaluationTs` (market as-of) and `assessedAt` (engine assessment instant) are never confused. The record
  enforces status ↔ reason consistency: `STALE` carries `FEATURE_SNAPSHOT_STALE` and/or
  `PROCESSING_LATENCY_EXCEEDED`, `CLOCK_SKEW` carries `SOURCE_CLOCK_SKEW` (plus `SOURCE_FUTURE_EVENT` when the
  age is negative), `FRESH` carries none. Policy durations must be whole milliseconds and at least 1 ms
  (sub-/fractional-millisecond durations are rejected instead of silently truncating).
- **Overall reason codes** live only in `QualityAssessment.interpretationQuality().reasonCodes()`;
  `QualityAssessment.reasonCodes()` is a read-through view, so there is exactly one explanation list.
- **Global policy**: `NO_DATA → NO_DATA`; `UNSAFE` / clock skew / stale / (policy-blocked) future event →
  `BLOCKED`, never eligible; source `OK` + all horizons eligible + fresh → `OK`; otherwise `DEGRADED`,
  eligible for trading iff at least one horizon is `ELIGIBLE` (meaning only that interpretation may
  continue). A hard gate does not rewrite a valid trade-flow horizon to `FAILED`.
- **Per-feature degradation**: typed `FeatureGroupId` (`bbo`, `order-book`, `trade-flow`,
  `short-term-regime`; unknown ids preserved). `trade-flow` failed → all horizons `FAILED`; other failed
  groups are kept in `failedFeatureGroups` and degrade overall quality without failing trade-flow horizons.

No directional logic, no opportunity, no V2 runtime path, no live `Clock` wiring yet; V1 (`mse-signals-v8`)
goldens, metrics and Kafka runtime are unchanged. Details: roadmap §15, "Етап 3".

## Multi-horizon flow evidence (Stage 4, pure, not yet wired)

`application/.../domain/interpretation/flow` is the first V2 directional evidence evaluator:
`FlowAssessmentEvaluator` turns a **validated** `MarketFeaturesSnapshot`, its Stage 3 `QualityAssessment`
and an explicit, versioned `FlowAssessmentPolicy` into `FlowAssessments` — exactly one `FLOW`
`EvidenceAssessment` per `MarketHorizon` (`1S, 5S, 15S, 60S`, canonical order, fail-fast lookup,
immutable, value equality; extra map entries rejected). Pure and deterministic (no
Spring/Kafka/Avro/`Clock`/metrics): same input + policy ⇒ value-equal result. The shared
`SnapshotQualityConsistencyGuard` (in `interpretation.quality`, used by **every** evidence evaluator)
cross-checks that the `QualityAssessment` was produced from the given snapshot — source status,
future-event flag, `evaluationTs`/`computedAt`, failed feature groups, and the full per-horizon
eligibilities re-derived through the canonical `HorizonEligibilityResolver` (a pure dependency; the
eligibility rules are never duplicated) — and fails fast on a mismatched pair, which also separates
two same-status DEGRADED snapshots whose degradation differs (incomplete book vs stale trades). The
guard runs exactly once per `evaluate(...)` call, before any feature value is read. This is not full
lineage binding: full identity binding via `sourceFeatureEventId` comes with the runtime assembler.
The output is heuristic **evidence** — not a probability, not a confidence, not BUY/SELL, not an
opportunity.

- **Policy** (`FlowAssessmentPolicy`, `FlowHorizonPolicy` per horizon; `BigDecimal` only, no defaults, no
  production values yet): `policyVersion` (non-blank, not a placeholder), and per horizon
  `bullishImbalanceThreshold ∈ (0,1]`, `bearishImbalanceThreshold ∈ [-1,0)` (strictly below bullish),
  `minTradeCount > 0`, `minAggressiveTradeCount ≥ 0`, `maxUnknownSideRatio ∈ [0,1]`. Missing / duplicate
  horizon fails fast. Thresholds are not calibrated; tests use an explicit fixture policy.
- **Eligibility first.** A horizon that Stage 3 did not mark `ELIGIBLE` is projected without reading any
  feature value through the shared `EvidenceEligibilityProjection` (used by every evidence evaluator) —
  `WARMING_UP`/`UNAVAILABLE → UNAVAILABLE`, `UNTRUSTED → UNTRUSTED`, `FAILED → FAILED`,
  `UNKNOWN → UNKNOWN` — with direction `UNKNOWN`, no strength and the eligibility reasons kept verbatim.
- **Per eligible horizon** (window chosen by the single canonical `TradeFlowFeature.window(horizon)`):
  missing window / `signedFlowImbalance` / activity counts → `UNAVAILABLE` (`FLOW_WINDOW_MISSING`,
  `FLOW_IMBALANCE_MISSING`, `FLOW_ACTIVITY_COUNTS_MISSING`; `null` is never zero); imbalance outside
  `[-1,1]`, negative counts or count contradictions → `UNTRUSTED` (`FLOW_IMBALANCE_OUT_OF_RANGE`,
  `FLOW_ACTIVITY_COUNTS_INVALID`); `tradeCount < minTradeCount` or `aggressiveTradeCount <
  minAggressiveTradeCount` → `AVAILABLE` + direction `UNKNOWN`, no strength (`FLOW_INSUFFICIENT_ACTIVITY`
  — **not** NEUTRAL); `unknownSideCount / tradeCount > maxUnknownSideRatio` → `UNTRUSTED`
  (`FLOW_UNKNOWN_SIDE_RATIO_EXCEEDED`; `ratio == max` still passes; deterministic `BigDecimal` division,
  scale `max(6, scale(max))`, `CEILING`); then `imbalance >= bullish → BULLISH`, `imbalance <= bearish →
  BEARISH`, otherwise `NEUTRAL` (boundaries inclusive on the directional side).
- **Strength** = `|signedFlowImbalance|` for BULLISH/BEARISH, a real `0` for NEUTRAL; an absent strength
  always means "could not be assessed". No `MIXED` from a single window; `tradeIntensity`,
  `totalAggressiveVolume`, `signedTradeFlow`, `avgTradeSize`, `vwap` are deliberately not gates or weights
  in Flow V1.

Details: roadmap §15, "Етап 4".

## Momentum, volatility and book evidence (Stage 5, pure, not yet wired)

`interpretation/momentum`, `interpretation/volatility` and `interpretation/book` add the remaining three
independent evidence dimensions, following the same shape as Flow: pure, deterministic evaluators
(no Spring/Kafka/Avro/`Clock`/metrics, `BigDecimal` only), an explicit versioned policy with no
production defaults, the shared `SnapshotQualityConsistencyGuard` (exactly once per `evaluate(...)`)
and the shared `EvidenceEligibilityProjection` (eligibility has the highest precedence in every
evaluator; non-eligible horizons keep their eligibility reasons verbatim and never become NEUTRAL).
All three produce strict, immutable four-horizon containers with value equality.

- **Momentum** (`MomentumAssessmentEvaluator` → `MomentumAssessments`, dimension `MOMENTUM`): one
  canonical selector maps `5S/15S/60S → RegimeFeature.priceChangeBps5s/15s/60s`; MFS v2 publishes no
  1S price change, so an eligible `1S` is explicitly `UNAVAILABLE` (`MOMENTUM_NOT_SCOPED_TO_HORIZON`)
  and is **never** substituted with the 5S value (the policy holds exactly three horizon policies —
  no 1S fiction). Failed `short-term-regime` → `FAILED`; missing regime/value → `UNAVAILABLE`;
  `abs(move) > maxSafeAbsMoveBps` → `UNTRUSTED` (the boundary itself is trusted). Direction thresholds
  are inclusive (`bullish > 0`, `bearish < 0`); strength = `min(1, abs(move)/fullStrengthAbsMoveBps)`
  (saturating, deterministic scale-6 `DOWN` division), NEUTRAL carries a real `0`. Momentum V1 reads
  only the horizon's own `priceChangeBps*s` — no VWAP, flow confirmation/divergence, or cross-horizon
  values (next interpretation layer).
- **Volatility** (`VolatilityAssessmentEvaluator` → `VolatilityAssessments` of typed
  `VolatilityAssessment`): one canonical selector maps all four horizons to
  `RegimeFeature.realizedVolatilityBps1s/5s/15s/60s` (the deprecated alias is never read). Volatility
  is a regime, **not a directional vote**: even AVAILABLE evidence reads direction `UNKNOWN` with no
  strength, and the typed `VolatilityLevel` (`LOW/NORMAL/HIGH/EXTREME`, inclusive upper bounds
  `0 ≤ low < normal < high`; `UNKNOWN` for non-available evidence) is a model value, never parsed from
  reason codes. Negative values → `UNTRUSTED`; `HIGH`/`EXTREME` neither block a horizon nor create a
  NO_TRADE here — context for the future regime/opportunity layer.
- **Book** (`BookAssessmentEvaluator` → `BookAssessments`, dimension `BOOK`): `BboFeature`/`BookFeature`
  are one **instantaneous** book snapshot, so only `1S` carries real book evidence; eligible
  `5S/15S/60S` are `UNAVAILABLE` (`BOOK_NOT_SCOPED_TO_HORIZON`) and the 1S reading is never copied
  out. For 1S: failed `bbo`/`order-book` groups → `FAILED` (both codes when both, checked before
  missing features); book-specific quality (`sourceOrderBookTrusted == false`, `syncStatus !=
  IN_SYNC`, `staleOrderBookState`, `incompleteBook`) → `UNTRUSTED` with all applicable codes —
  `staleTrades`, failed `trade-flow`/`short-term-regime` and non-book degradation are deliberately not
  book-specific faults; structurally invalid BBO geometry (non-positive/crossed prices, negative
  spread/quantity, microprice outside `[bid, ask]`), `levelsUsed ≤ 0`, out-of-range `top1/top5`
  imbalance or an implausible microprice offset → `UNTRUSTED`. Two indicators only —
  `BookFeature.top5Imbalance` (needs `levelsUsed ≥ minimumLevelsUsed`; insufficient depth drops it
  with `BOOK_INSUFFICIENT_DEPTH`) and `BboFeature.micropriceOffsetBps` (saturating strength like
  momentum): confirmation takes the weaker strength, conflict → `MIXED` with no strength, directional
  + neutral → the directional one, a single usable indicator → `BOOK_PARTIAL_EVIDENCE`, both missing →
  `UNAVAILABLE` (never neutral). Spread is validated as geometry but is **never** a directional vote
  and the policy has no `maxSpreadBps` — spread acceptance is a later execution/liquidity gate.

Flow/Momentum confirmation and divergence, `HorizonAssessment` aggregation, the cross-horizon
interpreter, `MarketRegimeResolver`, opportunity resolution, the V2 runtime path and production policy
values are not implemented yet; V1 (`mse-signals-v8`) goldens, metrics and Kafka runtime are
unchanged. Details: roadmap §15, "Етап 5".

## Failure behaviour, delivery semantics and metrics

- **Delivery is at-least-once.** The input offset is committed only after the output is
  acknowledged. A publish timeout abandons the wait, but `future.cancel()` does **not** guarantee the
  Kafka producer drops the record — a listener retry after an ambiguous timeout can produce a
  **duplicate** output. Downstream deduplicates on the deterministic `signalSnapshotId` (same input +
  config + `evaluatedAt` ⇒ same id).
- **Timeout hierarchy** (validated at startup, `PublishTimeoutHierarchy`; invalid values abort
  startup): `request.timeout.ms (3000) < delivery.timeout.ms (5000) < app.kafka.publish-timeout-ms (6500)`.
  The producer gives up before the application stops waiting, so most producer failures surface as
  explicit errors rather than ambiguous timeouts. The producer is idempotent (`enable.idempotence=true`,
  `acks=all`, forced in code).
- **Fail-fast publisher.** `MarketSignalSnapshotPublisher` throws on a `null` snapshot (no
  log-and-skip), refuses a blank topic or non-positive timeout at construction, keeps the bounded wait,
  restores the interrupt flag on `InterruptedException`, and lets every publish failure propagate to
  the listener error handler. `PublisherConfiguration` turns a blank `app.kafka.topic.market-signals`
  or an invalid timeout into an application startup failure.
- **Retry → DLT.** `SignalPublishException` (bounded publish failure/timeout) is retried with fixed
  back-off; `FixedBackOff.maxAttempts` (`APP_KAFKA_RETRY_MAX_ATTEMPTS`) counts retries *after* the first
  delivery, so `3` means 4 deliveries, then the original input record is dead-lettered to
  `<input-topic>.DLT`. `AvroMappingException` and `InvalidMarketFeaturesSnapshotException` are
  non-retryable and go straight to the DLT.

Micrometer metrics (actuator `/actuator/metrics/<name>`; unchanged in this stage):

| Metric | Tags | Meaning |
|---|---|---|
| `mse.snapshots` | `riskLevel`, `marketBias`, `setupSide` | Snapshots produced, by outcome |
| `mse.no_trade.reasons` | `type` | RISK_OFF signal types behind no-trade snapshots |
| `mse.input.age` | – | evaluatedAt − exchange event time (ms) |
| `mse.evaluate.duration` | – | validate + evaluate time |
| `mse.publish.duration` | `outcome=ok\|failed` | time to output broker ack |
| `mse.e2e.latency` | – | publish ack − exchange event time (ms) |
| `mse.consume.retries` / `mse.dlt.records` / `mse.dlt.failures` | `topic`, `exception` | listener retries, dead-lettered records, DLT publish failures |

Integration tests (`infrastructure/app`) run on an in-JVM `EmbeddedKafka` (KRaft) with a `mock://`
Schema Registry — no Docker needed: consume → evaluate → publish, duplicate input → same id,
unsupported contract → DLT, **publish failure → exact retry count → DLT with the original input and no
output** (`KafkaPublishFailureDltTest`), a full Spring context test that checks `spring.kafka.listener.*`
really reach the container, and startup-failure tests for invalid publisher configuration.

## Replay and golden tests

The engine is stateless and deterministic, so the `application` module ships an in-process replay
harness: `ReplayHarness.standard(config).replay(List<MarketFeaturesSnapshot>)` runs inputs through
the **same validated evaluator and rule wiring production uses** (`ValidatedMarketSignalEvaluator`
over `StandardSignalEngine`) and returns one `MarketSignalSnapshot` per input. Evaluation time is
pinned to the input's `computedAt` (or a fixed instant), never to the wall clock, so the same input
and config always give the same output. `ReplayHarness.standard(config, validator)` accepts an
explicit validator (e.g. a wider `featureSetVersion` allowlist for recorded data).

`ReplayGoldenTest` replays the synthetic fixtures in `GoldenFixtures` (complete MFS v2 inputs with
lineage: `schemaVersion`, `evaluationTs`, `triggerSource`, `configHash`, diagnostics) and compares
the rendered output with `application/src/test/resources/golden/<case>.txt`. Two fixtures are
contract-invalid on purpose (`quality-missing`, `quality-status-missing`): the validated replay rejects
them, and their goldens pin the engine's own defence-in-depth by calling the engine directly. A golden
mismatch means the engine's observable output changed:

```bash
# intended change: regenerate, review the diff, commit the golden update separately
GOLDEN_UPDATE=true ./gradlew :application:test --tests "*ReplayGoldenTest"
```

Any golden change that alters semantics must come with a `signalSetVersion` bump.

## Tech stack

- Java 21
- Spring Boot 3.3.x
- Gradle 9.x (Kotlin DSL)
- Apache Kafka + Confluent Schema Registry (Avro)
- Hexagonal architecture (ports & adapters)

## Modules

| Module | Purpose |
|--------|---------|
| `application` | Pure domain/application core: models, signal rules, engine, `MarketFeaturesHandler` |
| `infrastructure/app` | Spring Boot entrypoint, configuration properties, bean wiring |
| `infrastructure/event-adapter` | Kafka Avro consumer, event mappers, `MarketSignalSnapshotPublisher` |
