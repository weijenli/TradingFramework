# Trading Framework

A Java 21 / Maven framework that:

1. **Downloads** end-of-day prices as CSV over HTTP from several websites and **parses** them into a `StockBar` POJO that is saved to **CSV files** and a **database** (H2 via JDBC).
2. **Backtests** stock-picking, entry and exit strategies on historical data, including **parameter sweeps**, and reports **win rate** and **profit** (plus profit factor, drawdown, expectancy...).
3. **Daily scan:** runs once per trading day after the market closes, applies every strategy and lists the **stock picks along with the strategy that produced each one**.
4. **Real-time alerts:** subscribes to live prices (Finnhub WebSocket, or a simulated feed), applies alert strategies to a few watched stocks and **sends signals** (console, CSV log, Slack-style webhook).

## Quick start

```bash
mvn package                                   # build + run tests -> target/trading-framework-1.0.0-SNAPSHOT.jar
JAR=target/trading-framework-1.0.0-SNAPSHOT.jar

java -jar $JAR demo                           # offline end-to-end demo on synthetic data (no keys needed)

export EODHD_APIKEY=...                       # and/or TWELVEDATA_APIKEY, TIINGO_APIKEY, ALPHAVANTAGE_APIKEY
java -jar $JAR download                       # all symbols in config; or: download AAPL MSFT
java -jar $JAR backtest                       # every strategy with default parameters
java -jar $JAR backtest MA_CROSSOVER --sweep  # parameter sweep, ranked by profit
java -jar $JAR backtest BREAKOUT --trades --symbols AAPL,MSFT
java -jar $JAR daily                          # refresh data + today's picks (once)
java -jar $JAR daily --schedule               # every trading day at 16:30 New York time

export FINNHUB_APIKEY=...
java -jar $JAR realtime                       # live alerts; --simulated to use the random-walk feed
```

Settings live in `src/main/resources/application.properties` (defaults), with overrides in `config/application.properties` (or `-Dconfig=path`). Any key can also be set with an environment variable: upper-case it and replace dots with underscores, e.g. `finnhub.apiKey` → `FINNHUB_APIKEY`.

## Data sources

| Source | Key | CSV columns | Status (checked Oct 2026) |
|---|---|---|---|
| `eodhd` – eodhd.com | `eodhd.apiKey` (`demo` works for AAPL) | Date,Open,High,Low,Close,Adjusted_close,Volume | ✅ verified |
| `twelvedata` – twelvedata.com | `twelvedata.apiKey` (`demo` works for AAPL) | datetime,open,high,low,close,volume | ✅ verified |
| `tiingo` – tiingo.com | `tiingo.apiKey` | date,close,high,low,open,volume,adjClose,... | needs a free key |
| `alphavantage` – alphavantage.co | `alphavantage.apiKey` | timestamp,open,high,low,close,volume | needs a free key (`full` history may be premium) |
| `stooq` – stooq.com | none | Date,Open,High,Low,Close,Volume | ⚠️ currently returns a JavaScript bot-check page to non-browser clients |

Sources are tried in the order given by `data.sources`. A source is skipped if its key is missing, and the next source is used if one fails. Responses that aren't price CSV (HTML pages, JSON rate-limit messages) are rejected, and API keys are redacted from logs. Raw responses are archived in `data/raw/<source>/`. Updates are incremental, starting from the day after the last stored bar.

To add a source, implement `PriceDataSource.buildUri(...)`. `CsvBarParser` maps columns by header name, so most CSV layouts work without parser changes.

## Packages

| Package | Contents |
|---|---|
| `model` | `StockBar` (Serializable POJO: symbol, date, open, high, low, close, volume, adjClose) and `BarSeries` (indexed history with an indicator cache) |
| `data` | `PriceDataSource` implementations, `HttpCsvDownloader`, `CsvBarParser`, `StockDataService` facade, `SyntheticDataGenerator` |
| `storage` | `BarRepository` with `CsvFileBarRepository` (`data/eod/<SYMBOL>.csv`) and `JdbcBarRepository` (table `stock_bar`, upsert) |
| `indicator` | SMA, EMA, RSI, ATR, average volume, channel highs/lows |
| `strategy` | `PickStrategy`, `EntryStrategy`, `ExitStrategy` → `TradingStrategy`; built-ins in `Picks`, `Entries`, `Exits`; parameterised presets in `StrategyCatalog` |
| `backtest` | `BacktestEngine`, `BacktestResult` (metrics), `ParameterGrid` + `ParameterSweep`, `BacktestReport` |
| `daily` | `DailyScanner`, `StockPick`, `PickReportWriter` (`reports/picks-DATE.csv`), `MarketCloseScheduler` |
| `realtime` | `PriceFeed` (`FinnhubWebSocketFeed`, `SimulatedPriceFeed`), `LiveSeriesTracker`, `AlertStrategy` + `Alerts`, `AlertEngine`, `SignalSink` |
| `app` | `TradingFrameworkApp` CLI and `AppConfig` |

## Strategies

A `TradingStrategy` is made of three parts:

* **pick**: a screening filter, e.g. `price>=5 AND avgVol(20)>=300000`
* **entry**: when to buy, e.g. `SMA(10) crosses above SMA(50)`
* **exit**: when to sell, e.g. `stop-loss 8% OR take-profit 25% OR SMA cross down`

Built-in presets (`StrategyCatalog`):

| Name | Pick | Entry | Exit |
|---|---|---|---|
| `MA_CROSSOVER` | min price, min avg volume | fast SMA crosses above slow SMA | stop-loss, take-profit, MA cross down |
| `RSI_REVERSION` | min price, above 200-day SMA | RSI crosses back above oversold | stop-loss, max holding bars, RSI overbought |
| `BREAKOUT` | min price, above 50-day SMA | close above 20-day high on 1.5x volume | trailing stop, close below SMA |

Adding your own:

```java
StrategyCatalog.register("GAP_UP", p -> new TradingStrategy("GAP_UP", p,
        Picks.minAverageVolume(p.get("minVol", 1e6), 20),
        EntryStrategy.of("gap up > 3%", (s, i) -> i > 0 && s.open(i) > s.close(i - 1) * 1.03),
        ExitStrategy.anyOf(Exits.stopLoss(p.get("stop", 0.05)), Exits.maxHoldingBars(p.getInt("hold", 5)))));
```

## Backtest model

* Signals are evaluated at the close of bar *i*. Entries, and exits that don't specify a price, fill at the **open of bar i+1**, so the backtest never sees future data.
* Stops and targets fill **during the bar** at the stop/target price, or at the open if the price gaps through. When one bar touches both, the stop is assumed to have hit first.
* The backtest is long-only and holds at most one position per symbol. Each trade is sized at `initialCapital × positionSizePct`. Commission is charged on every order and slippage is applied against you on every fill.
* `from` only limits when new trades can start. Data before `from` is still used to warm up indicators.
* Metrics: trades, win rate, total profit and return, average return per trade, average win and loss, profit factor, expectancy, maximum drawdown of realised equity, and average holding time. Trades are also written to `reports/trades-<STRATEGY>.csv`.

```java
List<BacktestResult> ranked = new ParameterSweep().run(
        StrategyCatalog::maCrossover,
        new ParameterGrid().values("fast", 5, 10, 20).range("slow", 30, 200, 10).values("stopLoss", 0.05, 0.1),
        universe, BacktestConfig.defaults().withDateRange(LocalDate.of(2019, 1, 1), null),
        BacktestResult.BY_PROFIT_FACTOR);
```

## Real-time alerts

`LiveSeriesTracker` builds a live daily bar from the incoming ticks and appends it to the stored history. Because of this, **any daily strategy can be run intraday** with `Alerts.entrySignal(strategy)`, which answers "would this strategy enter if the day closed now?". Other built-in alerts:

* `priceCrossesAbove` / `priceCrossesBelow`, configured as `realtime.priceAlerts=AAPL>250,NVDA<150`
* `percentMoveFromPreviousClose`
* `volumeSpike`

Each (symbol, strategy) pair has a cooldown (`realtime.cooldownSeconds`) so the same signal doesn't repeat too often. To use a broker or exchange feed other than Finnhub, implement `PriceFeed`.

## Notes

* The NYSE holiday list in `daily.holidays` covers 2026–2027. Check it against the official calendar.
* `JdbcBarRepository` uses H2's `MERGE ... KEY` for upserts. To use PostgreSQL or another database, override `upsertSql()` and add the JDBC driver.
* For research and education only; this is not investment advice.
