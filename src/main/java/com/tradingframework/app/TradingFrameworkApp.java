package com.tradingframework.app;

import com.tradingframework.backtest.BacktestConfig;
import com.tradingframework.backtest.BacktestEngine;
import com.tradingframework.backtest.BacktestReport;
import com.tradingframework.backtest.BacktestResult;
import com.tradingframework.backtest.ParameterGrid;
import com.tradingframework.backtest.ParameterSweep;
import com.tradingframework.daily.DailyScanResult;
import com.tradingframework.daily.DailyScanner;
import com.tradingframework.daily.MarketCloseScheduler;
import com.tradingframework.daily.PickReportWriter;
import com.tradingframework.data.AlphaVantageDataSource;
import com.tradingframework.data.CsvBarParser;
import com.tradingframework.data.EodhdDataSource;
import com.tradingframework.data.TwelveDataSource;
import com.tradingframework.data.HttpCsvDownloader;
import com.tradingframework.data.PriceDataSource;
import com.tradingframework.data.StockDataService;
import com.tradingframework.data.StooqDataSource;
import com.tradingframework.data.SyntheticDataGenerator;
import com.tradingframework.data.TiingoDataSource;
import com.tradingframework.model.BarSeries;
import com.tradingframework.realtime.AlertEngine;
import com.tradingframework.realtime.AlertStrategy;
import com.tradingframework.realtime.Alerts;
import com.tradingframework.realtime.FinnhubWebSocketFeed;
import com.tradingframework.realtime.LiveSeriesTracker;
import com.tradingframework.realtime.PriceFeed;
import com.tradingframework.realtime.SignalSink;
import com.tradingframework.realtime.SimulatedPriceFeed;
import com.tradingframework.storage.BarRepository;
import com.tradingframework.storage.CsvFileBarRepository;
import com.tradingframework.storage.JdbcBarRepository;
import com.tradingframework.strategy.Parameters;
import com.tradingframework.strategy.StrategyCatalog;
import com.tradingframework.strategy.TradingStrategy;

import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Command-line entry point.
 * <pre>
 *   download [SYMBOL ...]                       download/refresh EOD data (CSV files + database)
 *   backtest [STRATEGY|all] [--sweep] [--trades] [--symbols A,B]
 *   daily    [--schedule] [--no-refresh]        today's picks (once, or every trading day after close)
 *   realtime [--simulated] [--minutes N]        stream quotes and send alert signals
 *   demo                                        offline end-to-end demo on synthetic data
 * </pre>
 */
public class TradingFrameworkApp {

    private static final PrintStream OUT = System.out;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        AppConfig config = new AppConfig(Path.of(System.getProperty("config", "config/application.properties")));
        List<String> rest = Arrays.asList(args).subList(1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "download" -> download(config, rest);
            case "backtest" -> backtest(config, rest);
            case "daily" -> daily(config, rest);
            case "realtime" -> realtime(config, rest);
            case "demo" -> demo();
            default -> usage();
        }
    }

    private static void usage() {
        OUT.println("""
                Usage: java -jar trading-framework.jar <command> [options]
                  download [SYMBOL ...]                     download/refresh EOD data
                  backtest [STRATEGY|all] [--sweep] [--trades] [--symbols A,B]
                  daily    [--schedule] [--no-refresh]      daily picks after market close
                  realtime [--simulated] [--minutes N]      real-time alert signals
                  demo                                      offline demo on synthetic data
                Strategies: """ + StrategyCatalog.names());
    }

    // ------------------------------------------------------------------ wiring

    static StockDataService dataService(AppConfig c) {
        Path dataDir = Path.of(c.get("data.dir", "data"));
        List<PriceDataSource> sources = new ArrayList<>();
        for (String name : c.getList("data.sources")) {
            switch (name.toLowerCase(Locale.ROOT)) {
                case "stooq" -> sources.add(new StooqDataSource());
                case "eodhd" -> sources.add(new EodhdDataSource(c.get("eodhd.apiKey")));
                case "twelvedata" -> sources.add(new TwelveDataSource(c.get("twelvedata.apiKey")));
                case "alphavantage" -> sources.add(new AlphaVantageDataSource(c.get("alphavantage.apiKey")));
                case "tiingo" -> sources.add(new TiingoDataSource(c.get("tiingo.apiKey")));
                default -> throw new IllegalArgumentException("Unknown data source " + name);
            }
        }
        List<BarRepository> repos = new ArrayList<>();
        repos.add(new CsvFileBarRepository(dataDir.resolve("eod")));
        if (c.getBoolean("db.enabled", true)) {
            repos.add(new JdbcBarRepository(c.get("db.url", "jdbc:h2:./data/db/trading"),
                    c.get("db.user", "sa"), c.get("db.password", "")));
        }
        HttpCsvDownloader downloader = new HttpCsvDownloader(
                c.getBoolean("data.archiveRaw", true) ? dataDir.resolve("raw") : null);
        return new StockDataService(sources, downloader, new CsvBarParser(), repos,
                c.getDate("data.historyStart", LocalDate.now().minusYears(10)));
    }

    static BacktestConfig backtestConfig(AppConfig c) {
        return new BacktestConfig(c.getDouble("backtest.initialCapital", 100_000),
                c.getDouble("backtest.positionSizePct", 0.10),
                c.getDouble("backtest.commission", 1.0),
                c.getDouble("backtest.slippagePct", 0.0005),
                c.getDate("backtest.from", null), c.getDate("backtest.to", null));
    }

    /** Parameter ranges explored by {@code backtest --sweep}. */
    static ParameterGrid sweepGrid(String strategy) {
        return switch (strategy) {
            case StrategyCatalog.MA_CROSSOVER -> new ParameterGrid()
                    .values("fast", 5, 10, 20).values("slow", 30, 50, 100, 200).values("stopLoss", 0.05, 0.08, 0.12);
            case StrategyCatalog.RSI_REVERSION -> new ParameterGrid()
                    .values("oversold", 25, 30, 35).values("overbought", 60, 70, 80).values("maxHoldBars", 10, 20);
            case StrategyCatalog.BREAKOUT -> new ParameterGrid()
                    .values("lookback", 20, 55, 100).values("volumeMultiple", 1.0, 1.5, 2.0).values("trailingStop", 0.08, 0.12, 0.20);
            default -> new ParameterGrid();
        };
    }

    // ------------------------------------------------------------------ commands

    static void download(AppConfig c, List<String> args) {
        StockDataService data = dataService(c);
        List<String> symbols = args.isEmpty() ? c.getList("symbols") : args;
        for (String sym : symbols) {
            try {
                OUT.printf("%-8s +%d bars%n", sym, data.update(sym));
            } catch (RuntimeException e) {
                OUT.printf("%-8s FAILED: %s%n", sym, e.getMessage());
            }
        }
    }

    static void backtest(AppConfig c, List<String> args) {
        StockDataService data = dataService(c);
        List<String> symbols = option(args, "--symbols").map(s -> List.of(s.split(","))).orElse(c.getList("symbols"));
        List<BarSeries> universe = symbols.stream().map(data::loadSeries).filter(s -> !s.isEmpty()).toList();
        if (universe.isEmpty()) {
            OUT.println("No data. Run 'download' first.");
            return;
        }
        String which = args.isEmpty() || args.get(0).startsWith("--") ? "all" : args.get(0).toUpperCase(Locale.ROOT);
        List<String> strategies = which.equals("ALL") ? StrategyCatalog.names() : List.of(which);
        runBacktests(universe, strategies, backtestConfig(c), args.contains("--sweep"), args.contains("--trades"),
                Path.of(c.get("backtest.reportDir", "reports")));
    }

    static void runBacktests(List<BarSeries> universe, List<String> strategies, BacktestConfig cfg,
                             boolean sweep, boolean showTrades, Path reportDir) {
        OUT.println("Universe: " + universe.stream().map(BarSeries::symbol).collect(Collectors.joining(",")));
        BacktestEngine engine = new BacktestEngine();
        for (String name : strategies) {
            if (sweep) {
                ParameterGrid grid = sweepGrid(name);
                OUT.printf("%n=== Parameter sweep: %s (%d combinations) ===%n", name, grid.size());
                List<BacktestResult> results = new ParameterSweep(engine).run(StrategyCatalog.factory(name), grid, universe, cfg);
                BacktestReport.printTable(results, OUT, 15);
                if (!results.isEmpty()) {
                    OUT.println("\nBest:\n" + results.get(0).summary());
                }
            } else {
                BacktestResult r = engine.run(StrategyCatalog.create(name, Parameters.empty()), universe, cfg);
                OUT.println("\n=== Backtest ===\n" + r.summary());
                if (showTrades) BacktestReport.printTrades(r, OUT, 50);
                Path csv = reportDir.resolve("trades-" + name + ".csv");
                BacktestReport.writeTradesCsv(r, csv);
                OUT.println("Trades written to " + csv);
            }
        }
    }

    static void daily(AppConfig c, List<String> args) {
        StockDataService data = dataService(c);
        DailyScanner scanner = new DailyScanner(data, StrategyCatalog.defaults(), 450, 7);
        PickReportWriter writer = new PickReportWriter(Path.of(c.get("daily.reportDir", "reports")));
        List<String> universe = c.getList("symbols");
        boolean refresh = !args.contains("--no-refresh");
        Runnable job = () -> {
            DailyScanResult result = scanner.scan(universe, refresh);
            result.print(OUT);
            OUT.println("Report: " + writer.write(result));
        };
        if (!args.contains("--schedule")) {
            job.run();
            return;
        }
        Set<LocalDate> holidays = c.getList("daily.holidays").stream().map(LocalDate::parse).collect(Collectors.toSet());
        MarketCloseScheduler scheduler = new MarketCloseScheduler(LocalTime.parse(c.get("daily.runTime", "16:30")),
                ZoneId.of(c.get("daily.zone", "America/New_York")), holidays);
        scheduler.start(job);
        OUT.println("Scheduler running; press Ctrl+C to stop.");
    }

    static void realtime(AppConfig c, List<String> args) throws InterruptedException {
        StockDataService data = dataService(c);
        List<String> symbols = c.getList("realtime.symbols");
        ZoneId zone = ZoneId.of(c.get("daily.zone", "America/New_York"));
        LiveSeriesTracker tracker = new LiveSeriesTracker(zone);
        LocalDate today = LocalDate.now(zone);
        Map<String, Double> lastClose = new HashMap<>();
        for (String sym : symbols) {
            BarSeries hist = data.loadSeries(sym, today.minusDays(450), today.minusDays(1));
            tracker.setHistory(hist);
            if (!hist.isEmpty()) lastClose.put(hist.symbol(), hist.last().getClose());
        }

        boolean simulated = args.contains("--simulated") || "simulated".equalsIgnoreCase(c.get("realtime.feed", "finnhub"));
        PriceFeed feed = simulated
                ? new SimulatedPriceFeed(lastClose, Duration.ofMillis(500), 0.002, System.nanoTime())
                : new FinnhubWebSocketFeed(c.get("finnhub.apiKey"));

        List<SignalSink> sinks = new ArrayList<>(List.of(SignalSink.console(OUT),
                SignalSink.csvFile(Path.of(c.get("realtime.signalLog", "reports/signals.csv")))));
        String webhook = c.get("realtime.webhookUrl");
        if (webhook != null) sinks.add(SignalSink.webhook(URI.create(webhook)));

        AlertEngine engine = new AlertEngine(feed, tracker, alertStrategies(c), sinks,
                Duration.ofSeconds((long) c.getDouble("realtime.cooldownSeconds", 900)), Duration.ofSeconds(1));
        engine.start(symbols);

        long minutes = option(args, "--minutes").map(Long::parseLong).orElse(0L);
        CountDownLatch forever = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(engine::close));
        if (minutes > 0) {
            Thread.sleep(Duration.ofMinutes(minutes));
            engine.close();
        } else {
            OUT.println("Streaming; press Ctrl+C to stop.");
            forever.await();
        }
    }

    /** Alert rules from config plus a BUY alert for every catalog strategy. */
    static List<AlertStrategy> alertStrategies(AppConfig c) {
        List<AlertStrategy> alerts = new ArrayList<>();
        for (String rule : c.getList("realtime.priceAlerts")) { // e.g. AAPL>250 or AAPL<180
            int gt = rule.indexOf('>'), lt = rule.indexOf('<');
            if (gt > 0) alerts.add(Alerts.priceCrossesAbove(rule.substring(0, gt).trim(), Double.parseDouble(rule.substring(gt + 1))));
            else if (lt > 0) alerts.add(Alerts.priceCrossesBelow(rule.substring(0, lt).trim(), Double.parseDouble(rule.substring(lt + 1))));
        }
        double move = c.getDouble("realtime.movePct", 0.03);
        if (move > 0) alerts.add(Alerts.percentMoveFromPreviousClose(move));
        double spike = c.getDouble("realtime.volumeSpike", 2.0);
        if (spike > 0) alerts.add(Alerts.volumeSpike(spike, 20));
        StrategyCatalog.defaults().forEach(s -> alerts.add(Alerts.entrySignal(s)));
        return alerts;
    }

    // ------------------------------------------------------------------ offline demo

    static void demo() throws Exception {
        Path dir = Files.createTempDirectory("trading-demo");
        OUT.println("Demo workspace: " + dir);
        List<String> symbols = List.of("ALPHA", "BRAVO", "CHARL", "DELTA", "ECHO", "FOXTR", "GOLF", "HOTEL");
        StockDataService data = new StockDataService(List.of(), new HttpCsvDownloader(), new CsvBarParser(),
                List.of(new CsvFileBarRepository(dir.resolve("eod")),
                        new JdbcBarRepository("jdbc:h2:" + dir.resolve("db/trading").toAbsolutePath())),
                LocalDate.of(2018, 1, 1));
        long seed = 42;
        for (String s : symbols) {
            data.save(SyntheticDataGenerator.generate(s, LocalDate.of(2018, 1, 2), 1800, 20 + seed % 80, seed));
            seed = seed * 31 + 7;
        }
        List<BarSeries> universe = symbols.stream().map(data::loadSeries).toList();
        OUT.println("Saved " + universe.stream().mapToInt(BarSeries::size).sum() + " synthetic bars to CSV files and H2.");

        BacktestConfig cfg = BacktestConfig.defaults().withDateRange(LocalDate.of(2019, 1, 1), null);
        runBacktests(universe, StrategyCatalog.names(), cfg, false, false, dir.resolve("reports"));
        runBacktests(universe, List.of(StrategyCatalog.MA_CROSSOVER), cfg, true, false, dir.resolve("reports"));

        LocalDate asOf = universe.get(0).last().getDate();
        List<TradingStrategy> loose = new ArrayList<>(StrategyCatalog.defaults());
        loose.add(StrategyCatalog.rsiReversion(Parameters.of("oversold", 45, "trendSma", 50))); // a looser variant
        DailyScanResult picks = new DailyScanner(data, loose, 450, 7).scan(symbols, false, asOf);
        OUT.println();
        picks.print(OUT);

        OUT.println("\n=== Real-time alerts (simulated feed, 5 seconds) ===");
        ZoneId zone = ZoneId.of("America/New_York");
        LiveSeriesTracker tracker = new LiveSeriesTracker(zone);
        Map<String, Double> lastClose = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(zone);
        for (String s : symbols.subList(0, 3)) {
            BarSeries hist = data.loadSeries(s, null, today.minusDays(1));
            tracker.setHistory(hist);
            lastClose.put(s, hist.last().getClose());
        }
        List<AlertStrategy> alerts = new ArrayList<>(List.of(Alerts.percentMoveFromPreviousClose(0.01)));
        String first = symbols.get(0);
        alerts.add(Alerts.priceCrossesAbove(first, Math.round(lastClose.get(first) * 1.005 * 100) / 100.0));
        StrategyCatalog.defaults().forEach(s -> alerts.add(Alerts.entrySignal(s)));
        try (AlertEngine engine = new AlertEngine(new SimulatedPriceFeed(lastClose, Duration.ofMillis(50), 0.003, 7),
                tracker, alerts, List.of(SignalSink.console(OUT)), Duration.ofSeconds(2), Duration.ZERO)) {
            engine.start(lastClose.keySet());
            Thread.sleep(5000);
            OUT.println("Signals sent: " + engine.sentSignals().size());
        }
    }

    private static Optional<String> option(List<String> args, String name) {
        int i = args.indexOf(name);
        return i >= 0 && i + 1 < args.size() ? Optional.of(args.get(i + 1)) : Optional.empty();
    }
}
