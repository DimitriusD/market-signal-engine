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
| `APP_SIGNAL_SET_VERSION` | `mse-signals-v7` | Signal set version label |
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
