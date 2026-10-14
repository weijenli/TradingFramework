package com.tradingframework.realtime;

import com.tradingframework.indicator.Indicators;
import com.tradingframework.strategy.TradingStrategy;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.tradingframework.realtime.Signal.SignalType;

/** Built-in alert strategies. */
public final class Alerts {

    private Alerts() {
    }

    /** Generic rule: fires {@code type} with {@code message} when {@code condition} holds. */
    public static AlertStrategy when(String name, String symbolOrNull, SignalType type,
                                     Predicate<AlertContext> condition, Function<AlertContext, String> message) {
        String only = symbolOrNull == null ? null : symbolOrNull.toUpperCase(Locale.ROOT);
        return new AlertStrategy() {
            @Override public String name() { return name; }
            @Override public boolean appliesTo(String symbol) { return only == null || only.equalsIgnoreCase(symbol); }
            @Override public Optional<Signal> evaluate(AlertContext ctx) {
                return condition.test(ctx) ? Optional.of(ctx.signal(type, name, message.apply(ctx))) : Optional.empty();
            }
            @Override public String toString() { return name; }
        };
    }

    public static AlertStrategy priceCrossesAbove(String symbol, double level) {
        return when(symbol + " crosses above " + level, symbol, SignalType.ALERT,
                c -> c.previousPrice() < level && c.price() >= level,
                c -> "price crossed above " + level);
    }

    public static AlertStrategy priceCrossesBelow(String symbol, double level) {
        return when(symbol + " crosses below " + level, symbol, SignalType.ALERT,
                c -> c.previousPrice() > level && c.price() <= level,
                c -> "price crossed below " + level);
    }

    /** Fires when price is at least {@code pct} (0.03 = 3%) away from yesterday's close. */
    public static AlertStrategy percentMoveFromPreviousClose(double pct) {
        return when("move >= " + pct * 100 + "% vs prev close", null, SignalType.ALERT,
                c -> Math.abs(c.price() / c.previousClose() - 1) >= pct,
                c -> String.format("%+.2f%% vs previous close %.2f", (c.price() / c.previousClose() - 1) * 100, c.previousClose()));
    }

    /** Fires when today's cumulative volume exceeds {@code multiple} x the average daily volume. */
    public static AlertStrategy volumeSpike(double multiple, int avgPeriod) {
        return when("volume > " + multiple + "x " + avgPeriod + "d avg", null, SignalType.ALERT,
                c -> c.series().volume(c.lastIndex()) > multiple * Indicators.averageVolume(c.series(), avgPeriod, c.lastIndex() - 1),
                c -> String.format("volume %,d vs avg %,.0f", c.series().volume(c.lastIndex()),
                        Indicators.averageVolume(c.series(), avgPeriod, c.lastIndex() - 1)));
    }

    /**
     * Applies a daily {@link TradingStrategy}'s pick + entry rules to the live bar: BUY when the
     * strategy would enter if the day closed at the current price.
     */
    public static AlertStrategy entrySignal(TradingStrategy strategy) {
        return when(strategy.label(), null, SignalType.BUY,
                c -> strategy.isEntrySignal(c.series(), c.lastIndex()),
                c -> "entry rule met: " + strategy.entry().name());
    }
}
