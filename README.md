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
state.market.features.v1
    ↓
MarketFeaturesKafkaConsumer
    ↓
MarketFeaturesSnapshotAvroMapper
    ↓
domain MarketFeaturesSnapshot
    ↓
MarketFeaturesHandler
    ↓
DefaultMarketSignalEngine
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
| `APP_KAFKA_PUBLISH_TIMEOUT_MS` | `5000` | Max wait for the output broker ack; on timeout the send is cancelled and the input goes through retry → DLT |
| `APP_KAFKA_RETRY_BACKOFF_MS` / `APP_KAFKA_RETRY_MAX_ATTEMPTS` | `1000` / `3` | Listener retry policy before dead-lettering to `<input>.DLT` (mapping/contract errors are not retried) |
| `APP_KAFKA_LISTENER_CONCURRENCY` / `_ACK_MODE` / `_POLL_TIMEOUT_MS` / `_AUTO_STARTUP` | `1` / `batch` / `1000` / `true` | `spring.kafka.listener.*`, applied through Boot's container-factory configurer |
| `APP_KAFKA_PRODUCER_ACKS` / `_DELIVERY_TIMEOUT_MS` / `_REQUEST_TIMEOUT_MS` | `all` / `10000` / `5000` | Output producer durability and bounded in-producer timeouts |
| `APP_KAFKA_CONSUMER_AUTO_OFFSET_RESET` | `latest` | Offset reset for a new consumer group |

## Operations: bounded failure and metrics

- **Publish is bounded.** `MarketSignalSnapshotPublisher` waits at most `APP_KAFKA_PUBLISH_TIMEOUT_MS` for the ack, then cancels the send and throws; the input record is retried with back-off and finally dead-lettered. The consumer thread never hangs on a slow broker.
- **Input failures go to `<input>.DLT`.** `AvroMappingException` and `InvalidMarketFeaturesSnapshotException` (unsupported `featureSetVersion`, missing identity) are not retried.
- **Duplicate input is idempotent downstream:** the same feature snapshot always yields the same `signalSnapshotId`.
- **Metrics** (Micrometer via actuator `/actuator/metrics`):

| Metric | Tags | Meaning |
|---|---|---|
| `mse.snapshots` | `riskLevel`, `marketBias`, `setupSide` | Snapshots produced |
| `mse.no_trade.reasons` | `type` | RISK_OFF signal types behind no-trade snapshots |
| `mse.input.age` | — | evaluatedAt − exchange event time (ms) |
| `mse.evaluate.duration` | — | validate + evaluate |
| `mse.publish.duration` | `outcome=ok\|failed` | time to broker ack |
| `mse.e2e.latency` | — | publish ack − exchange event time (ms) |
| `mse.consume.retries`, `mse.dlt.records`, `mse.dlt.failures` | `topic`, `exception` | listener retries, dead-lettered records, DLT publish failures |

Integration tests (`infrastructure/app`) run on an in-JVM `EmbeddedKafka` (KRaft) with a `mock://` Schema Registry — no Docker needed.
| `APP_KAFKA_PUBLISH_TIMEOUT_MS` | `5000` | Max wait for the output broker ack on the consumer thread; on timeout the send is cancelled and the input goes through retry → DLT |
| `APP_KAFKA_RETRY_BACKOFF_MS` / `APP_KAFKA_RETRY_MAX_ATTEMPTS` | `1000` / `3` | Listener retry policy before dead-lettering to `<input-topic>.DLT` (mapping/contract failures skip retries) |
| `APP_KAFKA_LISTENER_CONCURRENCY` / `..._ACK_MODE` / `..._POLL_TIMEOUT_MS` / `..._AUTO_STARTUP` | `1` / `batch` / `1000` / `true` | Standard `spring.kafka.listener.*`, applied through Boot's container factory configurer |
| `APP_KAFKA_PRODUCER_ACKS` / `..._DELIVERY_TIMEOUT_MS` / `..._REQUEST_TIMEOUT_MS` | `all` / `10000` / `5000` | Output producer durability and in-producer time bounds |
| `APP_KAFKA_CONSUMER_AUTO_OFFSET_RESET` | `latest` | Where a new consumer group starts |

## Failure behaviour and metrics

Every failure path is bounded: a slow broker cannot block the consumer thread past
`APP_KAFKA_PUBLISH_TIMEOUT_MS`; a failed publish is retried with back-off and then the input record is
dead-lettered; Avro mapping errors and contract violations (blank identity, unsupported
`featureSetVersion`) are non-retryable and go straight to `<input-topic>.DLT`. Input offsets are only
committed after the output is acknowledged (at-least-once; duplicates carry the same deterministic
`signalSnapshotId`).

Micrometer metrics (actuator `/actuator/metrics/<name>`):

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
unsupported contract → DLT, plus a full Spring context test that checks `spring.kafka.listener.*`
really reach the container.

## Replay and golden tests

The engine is stateless and deterministic, so the `application` module ships an in-process replay
harness: `ReplayHarness.standard(config).replay(List<MarketFeaturesSnapshot>)` runs inputs through
the **same** rule wiring production uses (`StandardSignalEngine`) and returns one
`MarketSignalSnapshot` per input. Evaluation time is pinned to the input's `computedAt` (or a fixed
instant), never to the wall clock, so the same input and config always give the same output.

`ReplayGoldenTest` replays the synthetic fixtures in `GoldenFixtures` and compares the rendered
output with `application/src/test/resources/golden/<case>.txt`. A golden mismatch means the
engine's observable output changed:

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
