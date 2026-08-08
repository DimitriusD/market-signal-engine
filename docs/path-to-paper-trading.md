# Market Signal Engine: шлях до paper trading

**Статус:** прийнято (рішення §8 зафіксовані)
**Дата:** 2026-08-08
**Базовий документ:** [signal-engine-target-architecture-and-roadmap.md](signal-engine-target-architecture-and-roadmap.md)
**Scope:** що саме і в якому порядку робимо, щоб дійти від поточного `mse-signals-v6` до працюючого paper trading

## 1. Призначення документа

Roadmap-документ фіксує цільову архітектуру і повну послідовність фаз. Цей документ — операційний зріз поверх нього:

- фіксує результати code review від 2026-08-08 (усі претензії roadmap перевірені за кодом і підтверджені);
- фіксує скориговані пріоритети, до яких ми прийшли в обговоренні;
- визначає, що означає milestone «paper trading ready» для Signal Engine;
- містить план блоків робіт із Definition of Done;
- перелічує відкриті питання, які потребують спільного рішення до початку кодування.

Після узгодження цей документ стає підставою для реалізаційних задач. Жодна робота з блоків 0+ не починається до закриття відкритих питань розділу 8.

## 2. Підтверджений поточний стан (review 2026-08-08)

Ревю звірило roadmap із фактичним кодом. Усі твердження підтверджені:

| # | Факт | Статус | Вплив |
|---|---|---|---|
| 1 | Topic mismatch: MFS публікує в `market.feature.snapshot.v1`, engine слухає `market.features.snapshot.v1` | Підтверджено | **Блокер**: сервіси не з'єднані за default config |
| 2 | `shortTermVolatility1s` у MFS v2 несе log-return **bps** (deprecated alias до `realizedVolatilityBps1s`), поріг engine — `0.01` у старих одиницях | Підтверджено | **Блокер**: на живих даних майже постійний хибний `VOLATILITY_HIGH → RISK_OFF` |
| 3 | Directional leakage: у Phase 3 попередні bullish/bearish signals лишаються в published list, якщо наступне directional rule дало invalid-feature `RISK_OFF` | Підтверджено | Порушення інваріанту публікації; bias/score/setup при цьому безпечні |
| 4 | Publisher робить `send().join()` без timeout | Підтверджено | Consumer thread може блокуватися до producer timeout |
| 5 | Listener factory зібрана вручну, `spring.kafka.listener.*` застосовуються не повністю | Підтверджено | Керованість у проді |
| 6 | Mapper ігнорує MFS v2 поля: aggregate `status`, `qualityReasons`, `warmingUp`, `futureEventDetected`, `evaluationTs`, `configHash`, `triggerSource`, `diagnostics`, 15s/60s trade flow, `realizedVolatilityBps*`, `priceChangeBps*`, `highLowRangeBps60s` | Підтверджено | Втрата multi-horizon context і quality semantics |
| 7 | README застарілий: топіки `state.market.features.v1`, `mse-signals-v1`, env-змінні під старий `signedTradeFlow5s` | Підтверджено | Дезінформує |

Сильні сторони, які зберігаємо без змін: трифазний gate, fail-closed null semantics, `DirectionalReduction` з інваріантами за конструкцією, deterministic snapshot ID, injected `Clock`, чиста гексагональна межа, 130 зелених тестів.

## 3. Скориговані пріоритети відносно roadmap

Це головні зміни до порядку робіт roadmap, до яких ми прийшли. Кожна — окреме рішення на підтвердження.

### Коригування A. Replay harness переноситься з Фази 8 на початок

Engine уже stateless і детермінований, тому мінімальний replay
(`List<MarketFeaturesSnapshot> → List<MarketSignalSnapshot>`) дешевий уже зараз.
Без нього Фази 4–6 (пороги, ваги, taxonomy) проєктуються наосліп.
Кожен поріг, доданий після появи replay, калібрується на записаних даних, а не вигадується.

### Коригування B. Data flywheel вже частково існує

Окрема джоба вже читає **всі** топіки, які публікують сервіси, і складає в даталейк.
Отже, recorder будувати не треба. Залишається:

1. верифікувати fidelity даталейку (див. розділ 5);
2. написати loader `даталейк-формат → MarketFeaturesSnapshot`;
3. написати сам replay harness + golden tests.

### Коригування C. Стриманий taxonomy до емпіричних доказів

Перша версія assessment states — мінімальна:
`BULLISH / BEARISH / NEUTRAL / INSUFFICIENT / UNAVAILABLE` per horizon.
Стани `DIVERGENT`, `POSSIBLE_ABSORPTION`, `POSSIBLE_EXHAUSTION` тощо додаються
тільки після того, як replay покаже, що вони розрізнювані та інформативні.

### Коригування D. Цільові акценти «ідеального» engine

Фіксуємо як довгострокові принципи (уточнення до roadmap, не заміна):

1. **Калібровані розподіли замість напрямків.** Кінцевий продукт — `P(up)/P(down)/P(flat)`,
   очікуваний рух у bps, квантилі q10/q50/q90 per horizon. Поле називається
   probability/confidence лише після підтвердженої калібрації (reliability diagrams,
   Brier/log-loss на walk-forward). До того — `evidenceStrength`.
2. **Net edge — найважливіше поле контракту.** Gross сигнали без cost-моделі генерують
   стратегії, що системно втрачають на спреді й комісіях. Cost-модель повинна включати
   adverse selection (вимірюється тільки з paper/live fills — ще один аргумент за ранній paper).
3. **Uncertainty і latency — першокласні величини.** Деградація quality/activity/спреду
   підвищує uncertainty і required edge безперервно, а не лише перемикає бінарний gate.
   End-to-end latency (exchange event → signal published) — вимірюваний SLO, зашитий у validity.
4. **Regime — кондиціонер калібрації**, а не просто одна з фіч: пороги/ваги калібруються
   per regime (мінімум low/normal/high volatility × activity).
5. **Негативне знання публікується структуровано**: `NO_EDGE: gross X bps < required Y bps` —
   це фідбек-канал для пошуку нових opportunity families і зниження костів.
6. **Sizing/позиції/ордери — ніколи не в Signal Engine.** Підтверджуємо межу з roadmap §3.3.

## 4. Що означає «paper trading ready»

Paper trading = повний ланцюг на живих даних без реальних ордерів:

```text
MFS v2 (live)
→ Signal Engine (live, цей репозиторій)
→ Strategy/Paper-Executor (окремий сервіс, поза scope цього репо)
→ simulated fills → outcome записується в даталейк
```

Для Signal Engine milestone означає:

1. engine споживає **живий** MFS v2 потік без втрат і без хибних gate-спрацювань;
2. published snapshot містить достатньо інформації, щоб paper-стратегія могла
   прийняти рішення і зафіксувати результат: bias/score, setup, validity, typed reasons,
   повний lineage;
3. кожен published snapshot відтворюваний із даталейку через replay (bit-for-bit
   semantic equality);
4. відмови мають bounded behavior: DLT працює, publisher не висить нескінченно;
5. є метрики, щоб відповісти «чому сигнал був/не був» без читання логів.

Paper trading **не вимагає**: V2 output contract, forecasts, opportunities, cost-моделі,
калібрації. Це все — наступні milestone поверх накопичених paper-даних.

## 5. Верифікація даталейку (передумова flywheel)

Чекліст до підтвердження по існуючій джобі:

- [ ] **Вхідний топік записується**: `market.feature.snapshot.v1` (фічі — критичні; сигнали ми
  завжди перерахуємо з фічей, фічі з сигналів — ніколи);
- [ ] **Повна fidelity**: raw Avro без семплінгу/обрізання полів, з partition/offset;
- [ ] **Writer schema зберігається** (schema id або сам schema) — інакше старі дані
  стануть нечитабельними після еволюції контракту;
- [ ] **Щільність для лейблів**: каданс feature snapshots достатній, щоб узяти BBO
  на t+1s/5s/15s/60s для executable-price лейблів; якщо є діри — джоба має
  додатково записувати сирий BBO/trades топік;
- [ ] **Retention** покриває горизонт калібрації (тижні, не дні).

## 6. План блоків до paper trading

### Блок 0. З'єднати сервіси і прибрати блокери → `mse-signals-v7`

| # | Робота | Деталь |
|---|---|---|
| 0.1 | Уніфікувати топік | Default engine → канонічна назва (див. відкрите питання 8.1) |
| 0.2 | Volatility gate на bps | Мапити `realizedVolatilityBps1s`; правило читає його; поріг у bps, позначений uncalibrated; deprecated поле більше не використовується domain-логікою |
| 0.3 | Directional leakage fix | No-trade snapshot не містить bullish/bearish signals; інваріантний тест |
| 0.4 | README оновити | Актуальні топіки, версія, env-змінні |
| 0.5 | Bump `signalSetVersion` | `mse-signals-v7` (зміна semantics gate) |
| 0.6 | E2E smoke | docker compose: MFS → engine → signals topic, побачити живий сигнал |

**DoD:** живий MFS v2 snapshot проходить крізь engine без хибного RISK_OFF;
no-trade snapshot без directional evidence; build зелений; README відповідає коду.

### Блок 1. Replay поверх даталейку

| # | Робота | Деталь |
|---|---|---|
| 1.1 | Верифікація даталейку | Чекліст розділу 5 |
| 1.2 | Loader | даталейк-формат → `MarketFeaturesSnapshot` |
| 1.3 | Replay harness | `List<MarketFeaturesSnapshot> → List<MarketSignalSnapshot>`, in-process, в `application` модулі |
| 1.4 | Golden tests | Зафіксовані входи → зафіксовані виходи; регресійний захист для всіх наступних блоків |

**DoD:** будь-який день із даталейку відтворюється локально; однаковий input/config
дає однаковий semantic output; golden tests у CI.

### Блок 2. Повний MFS v2 input (roadmap Фаза 1)

| # | Робота | Деталь |
|---|---|---|
| 2.1 | Мапінг усіх полів | quality `status`/`qualityReasons`/`warmingUp`/`futureEventDetected`, `evaluationTs`, `configHash`, `triggerSource`, `diagnostics`, 15s/60s trade flow, `realizedVolatilityBps*`, `priceChangeBps*`, `highLowRangeBps60s` |
| 2.2 | Contract tests | Кожне нове поле + null semantics |
| 2.3 | Compatibility validator | Невідома `featureSetVersion`/контракт → fail closed у DLT |
| 2.4 | Quality gate на aggregate status | `UNSAFE`/`NO_DATA` → hard block; `DEGRADED` → політика (див. 8.4) |

**DoD:** roadmap Фаза 1 DoD + жодне нове поле не «мовчазно ігнорується».

### Блок 3. Мінімальні eligibility/assessments для paper (roadmap Фази 2–3, стиснуто)

| # | Робота | Деталь |
|---|---|---|
| 3.1 | Per-horizon eligibility | `WARMING_UP` 60s не блокує 1s/5s; failed feature group блокує лише залежні оцінки |
| 3.2 | Typed reasons | У published snapshot видно, чому горизонт ineligible |
| 3.3 | Мінімальний taxonomy | Згідно з Коригуванням C |

**DoD:** null ніколи не стає neutral zero; короткі горизонти працюють під час warm-up;
причини видимі downstream.

### Блок 4. Production hardening мінімум для paper (roadmap Фаза 11, зріз)

| # | Робота | Деталь |
|---|---|---|
| 4.1 | Publisher timeout | Bounded `send()` замість нескінченного `join()` |
| 4.2 | Listener через Boot configurer | `spring.kafka.listener.*` працюють |
| 4.3 | Spring context test + Kafka integration tests | consume→evaluate→publish, DLT, duplicate input |
| 4.4 | Метрики | input age, evaluation/publish latency, e2e latency, eligibility/no-trade причини, DLT counts |

**DoD:** broker/registry outage має bounded behavior; duplicate input → stable ID;
метрики відповідають на «чому не було сигналу».

### Блок 5. Paper trading запуск

| # | Робота | Деталь |
|---|---|---|
| 5.1 | Контракт із paper-executor | Узгодити, що стратегії достатньо V1 snapshot (див. 8.3) |
| 5.2 | Запуск ланцюга | MFS → engine → paper-executor на живих даних |
| 5.3 | Outcome capture | Simulated fills + executable-price лейбли пишуться в даталейк |
| 5.4 | Моніторинг | Signal churn, validity expiry rate, no-trade частка |

**DoD:** ланцюг працює безперервно; кожне paper-рішення трасується до
signal snapshot → feature snapshot → config версій.

### Після paper (не в цьому milestone)

Калібрація порогів на накопичених outcome (roadmap Фаза 8) → cost-модель і net edge
(Фаза 10) → V2 контракт з assessments/opportunities (Фази 5–7) → forecasts (Фаза 9).
Порядок цих етапів уточнимо за результатами перших тижнів paper даних.

## 7. Інваріанти milestone

Додатково до roadmap §19, для paper trading:

1. Paper-рішення ніколи не приймається на expired snapshot (`validUntil` перевіряється executor-ом).
2. Кожен paper fill має посилання на `signalSnapshotId` і `sourceFeatureSnapshotId`.
3. Signal Engine не знає про існування paper-executor (звичайний Kafka consumer).
4. Зміна будь-якого порога під час paper-періоду = новий `signalSetVersion`, видимий в аналітиці.

## 8. Прийняті рішення

**Зафіксовано 2026-08-08.** Питання закриті; зміна будь-якого рішення — через явну правку цього розділу.

| # | Питання | Рішення | Обґрунтування |
|---|---|---|---|
| 8.1 | Канонічна назва feature-топіка | **`market.feature.snapshot.v1`** (як публікує MFS); engine міняє свій default | Міняти споживача — один рядок; міняти продюсера — розрив безперервності історії в даталейку |
| 8.2 | Placeholder поріг volatility у bps | **Свідомо щедрий поріг, що ріже лише явний екстрим, з атрибутом `uncalibrated` у snapshot.** Точне значення — перший результат replay після Блоку 1 | Будь-яке число до replay — вигадане; калібрація цього порога стане першою демонстрацією повного flywheel-циклу |
| 8.3 | Контракт для paper | **V1 `MarketSignalSnapshotEvent` як є.** V2 — пізніше, у shadow mode | Найважливіше рішення: чекати V2 = відкласти на місяці накопичення outcome-даних (включно з adverse selection), які не можна отримати ретроспективно |
| 8.4 | Політика `DEGRADED` quality status | **Hard block для paper-періоду.** Перегляд — після накопичення replay-даних про частоту і причини `DEGRADED` | Консервативність у paper нічого не коштує, а статистика для пом'якшення накопичиться сама |
| 8.5 | Обсяг Блоку 2 для paper | **Повний мапінг усіх полів MFS v2** | Різниця в зусиллях — день-два; кожне незамаплене поле — незаповнювана діра в paper-датасеті |
| 8.6 | Де живе paper-executor | **Окремий сервіс поза цим репо.** Engine про нього не знає; узгоджується лише контракт споживання | Межа відповідальності з roadmap §3.3 недоторканна |

## 9. Порядок виконання

1. ~~Закрити питання 8.1–8.6~~ — зроблено 2026-08-08.
2. Оновити roadmap §15 посиланням на цей план (щоб не було двох джерел правди).
3. Виконати Блоки 0 → 1 → 2 → 3 → 4 → 5 послідовно; кожен блок — окрема гілка/PR зі своїм DoD.
