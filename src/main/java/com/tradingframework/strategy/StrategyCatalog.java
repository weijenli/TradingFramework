package com.tradingframework.strategy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Predefined, parameterised strategies. Each factory merges the given parameters over its defaults,
 * so {@code create("MA_CROSSOVER", Parameters.of("fast", 20))} changes only {@code fast}.
 * Register custom strategies with {@link #register}.
 */
public final class StrategyCatalog {

    public static final String MA_CROSSOVER = "MA_CROSSOVER";
    public static final String RSI_REVERSION = "RSI_REVERSION";
    public static final String BREAKOUT = "BREAKOUT";

    private static final Map<String, Function<Parameters, TradingStrategy>> FACTORIES = new LinkedHashMap<>();

    static {
        register(MA_CROSSOVER, StrategyCatalog::maCrossover);
        register(RSI_REVERSION, StrategyCatalog::rsiReversion);
        register(BREAKOUT, StrategyCatalog::breakout);
    }

    private StrategyCatalog() {
    }

    public static synchronized void register(String name, Function<Parameters, TradingStrategy> factory) {
        FACTORIES.put(name, factory);
    }

    public static synchronized List<String> names() {
        return List.copyOf(FACTORIES.keySet());
    }

    public static synchronized Function<Parameters, TradingStrategy> factory(String name) {
        Function<Parameters, TradingStrategy> f = FACTORIES.get(name);
        if (f == null) throw new IllegalArgumentException("Unknown strategy '" + name + "'. Known: " + FACTORIES.keySet());
        return f;
    }

    public static TradingStrategy create(String name, Parameters params) {
        return factory(name).apply(params);
    }

    /** Every registered strategy with default parameters. */
    public static List<TradingStrategy> defaults() {
        return names().stream().map(n -> create(n, Parameters.empty())).toList();
    }

    // ---------------------------------------------------------------- trend following

    public static final Parameters MA_CROSSOVER_DEFAULTS = Parameters.of(
            "fast", 10, "slow", 50, "stopLoss", 0.08, "takeProfit", 0.25, "minPrice", 5, "minAvgVolume", 300_000);

    public static TradingStrategy maCrossover(Parameters overrides) {
        Parameters p = MA_CROSSOVER_DEFAULTS.overriddenBy(overrides);
        int fast = p.getInt("fast", 10), slow = p.getInt("slow", 50);
        if (fast >= slow) throw new IllegalArgumentException("fast must be < slow: " + p);
        return new TradingStrategy(MA_CROSSOVER, p,
                PickStrategy.all(Picks.minPrice(p.get("minPrice", 5)), Picks.minAverageVolume(p.get("minAvgVolume", 0), 20)),
                Entries.movingAverageCross(fast, slow),
                ExitStrategy.anyOf(Exits.stopLoss(p.get("stopLoss", 0.08)),
                        Exits.takeProfit(p.get("takeProfit", 0.25)),
                        Exits.movingAverageCrossDown(fast, slow)));
    }

    // ---------------------------------------------------------------- mean reversion

    public static final Parameters RSI_REVERSION_DEFAULTS = Parameters.of(
            "rsiPeriod", 14, "oversold", 30, "overbought", 70, "trendSma", 200,
            "stopLoss", 0.10, "maxHoldBars", 20, "minPrice", 5);

    public static TradingStrategy rsiReversion(Parameters overrides) {
        Parameters p = RSI_REVERSION_DEFAULTS.overriddenBy(overrides);
        int period = p.getInt("rsiPeriod", 14);
        return new TradingStrategy(RSI_REVERSION, p,
                PickStrategy.all(Picks.minPrice(p.get("minPrice", 5)), Picks.aboveSma(p.getInt("trendSma", 200))),
                Entries.rsiCrossesAbove(period, p.get("oversold", 30)),
                ExitStrategy.anyOf(Exits.stopLoss(p.get("stopLoss", 0.10)),
                        Exits.maxHoldingBars(p.getInt("maxHoldBars", 20)),
                        Exits.rsiAbove(period, p.get("overbought", 70))));
    }

    // ---------------------------------------------------------------- momentum breakout

    public static final Parameters BREAKOUT_DEFAULTS = Parameters.of(
            "lookback", 20, "volumeMultiple", 1.5, "trailingStop", 0.10, "trendSma", 50, "minPrice", 5);

    public static TradingStrategy breakout(Parameters overrides) {
        Parameters p = BREAKOUT_DEFAULTS.overriddenBy(overrides);
        return new TradingStrategy(BREAKOUT, p,
                PickStrategy.all(Picks.minPrice(p.get("minPrice", 5)), Picks.aboveSma(p.getInt("trendSma", 50))),
                Entries.breakout(p.getInt("lookback", 20), p.get("volumeMultiple", 1.5), 20),
                ExitStrategy.anyOf(Exits.trailingStop(p.get("trailingStop", 0.10)),
                        Exits.closeBelowSma(p.getInt("trendSma", 50))));
    }
}
