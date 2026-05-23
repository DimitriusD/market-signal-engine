# market-signal-engine

Event-driven Kafka Avro service that consumes market feature snapshots and produces interpreted market signal snapshots.

## What it does

`market-signal-engine` reads normalized market feature snapshots from Kafka, evaluates explainable signal rules in a pure domain/application core, and publishes (or temporarily logs) market signal snapshots.

## Input

| Property | Value |
|----------|-------|
| Topic | `state.market.features.v1` |
| Key | `instrumentId` |
| Value | `com.trading.contracts.feature.MarketFeaturesSnapshotEvent` |

## Output

| Property | Value |
|----------|-------|
| Topic | `state.market.signals.v1` |
| Key | `instrumentId` |
| Value | `com.trading.contracts.signal.MarketSignalSnapshotEvent` |

Output publisher is temporarily implemented as `LoggingMarketSignalSnapshotPublisher` until signal Avro contracts are published in `trading-schemas`.

## Architecture

```
market-signal-engine
    ├── application              # domain models, rules, use cases, ports (no Kafka/Avro/Spring)
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
MarketFeaturesEventMapper
    ↓
domain MarketFeaturesSnapshot
    ↓
EvaluateMarketSignalsUseCase
    ↓
DefaultMarketSignalEngine
    ↓
domain MarketSignalSnapshot
    ↓
LoggingMarketSignalSnapshotPublisher (temporary)
    ↓
state.market.signals.v1 (when Avro contract is available)
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
| `APP_KAFKA_TOPIC_MARKET_FEATURES` | `state.market.features.v1` | Input topic for feature snapshots |
| `APP_KAFKA_TOPIC_MARKET_SIGNALS` | `state.market.signals.v1` | Output topic for signal snapshots |
| `APP_SIGNAL_SET_VERSION` | `mse-signals-v1` | Signal set version label |
| `APP_SIGNAL_MAX_SPREAD_BPS` | `2.0` | Max acceptable spread in bps |
| `APP_SIGNAL_BUY_SIGNED_TRADE_FLOW_5S_THRESHOLD` | `0.0` | Buy pressure threshold for signedTradeFlow5s |
| `APP_SIGNAL_SELL_SIGNED_TRADE_FLOW_5S_THRESHOLD` | `0.0` | Sell pressure threshold for signedTradeFlow5s |
| `APP_SIGNAL_BUY_BOOK_IMBALANCE_THRESHOLD` | `0.60` | Bullish top5Imbalance threshold |
| `APP_SIGNAL_SELL_BOOK_IMBALANCE_THRESHOLD` | `-0.60` | Bearish top5Imbalance threshold |
| `APP_SIGNAL_MAX_SHORT_TERM_VOLATILITY_1S` | `0.01` | Max acceptable short-term volatility |

## Tech stack

- Java 21
- Spring Boot 3.3.x
- Gradle 9.x (Kotlin DSL)
- Apache Kafka + Confluent Schema Registry (Avro)
- Hexagonal architecture (ports & adapters)

## Modules

| Module | Purpose |
|--------|---------|
| `application` | Pure domain/application core: models, signal rules, engine, ports |
| `infrastructure/app` | Spring Boot entrypoint, configuration properties, bean wiring |
| `infrastructure/event-adapter` | Kafka Avro consumer, event mappers, publisher adapter |
