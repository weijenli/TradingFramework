package com.tradingframework.strategy;

import java.util.Optional;

import static com.tradingframework.indicator.Indicators.rsi;
import static com.tradingframework.indicator.Indicators.sma;

/** Built-in exit rules. */
public final class Exits {

    private Exits() {
    }

    /** Sell if price falls {@code pct} (0.08 = 8%) below entry; fills at the stop or the gap-down open. */
    public static ExitStrategy stopLoss(double pct) {
        return ExitStrategy.of("stop-loss " + pct(pct), (s, i, p) -> {
            double stop = p.entryPrice() * (1 - pct);
            return s.low(i) <= stop
                    ? Optional.of(ExitSignal.atPrice("STOP_LOSS", Math.min(s.open(i), stop)))
                    : Optional.empty();
        });
    }

    /** Sell if price rises {@code pct} above entry; fills at the target or the gap-up open. */
    public static ExitStrategy takeProfit(double pct) {
        return ExitStrategy.of("take-profit " + pct(pct), (s, i, p) -> {
            double target = p.entryPrice() * (1 + pct);
            return s.high(i) >= target
                    ? Optional.of(ExitSignal.atPrice("TAKE_PROFIT", Math.max(s.open(i), target)))
                    : Optional.empty();
        });
    }

    /** Sell if price falls {@code pct} below the highest high since entry. */
    public static ExitStrategy trailingStop(double pct) {
        return ExitStrategy.of("trailing-stop " + pct(pct), (s, i, p) -> {
            double level = p.highestHigh() * (1 - pct);
            return s.low(i) <= level
                    ? Optional.of(ExitSignal.atPrice("TRAILING_STOP", Math.min(s.open(i), level)))
                    : Optional.empty();
        });
    }

    /** Sell at the close after holding {@code bars} bars. */
    public static ExitStrategy maxHoldingBars(int bars) {
        return ExitStrategy.of("max-hold " + bars + " bars", (s, i, p) ->
                p.barsHeld(i) >= bars ? Optional.of(ExitSignal.atPrice("TIME_EXIT", s.close(i))) : Optional.empty());
    }

    /** Sell next open when fast SMA crosses below slow SMA. */
    public static ExitStrategy movingAverageCrossDown(int fast, int slow) {
        return ExitStrategy.of("SMA(" + fast + ") crosses below SMA(" + slow + ")", (s, i, p) ->
                i >= 1 && sma(s, fast, i) < sma(s, slow, i) && sma(s, fast, i - 1) >= sma(s, slow, i - 1)
                        ? Optional.of(ExitSignal.atNextOpen("MA_CROSS_DOWN"))
                        : Optional.empty());
    }

    /** Sell next open when RSI is above a threshold (overbought). */
    public static ExitStrategy rsiAbove(int period, double threshold) {
        return ExitStrategy.of("RSI(" + period + ") > " + Parameters.format(threshold), (s, i, p) ->
                rsi(s, period, i) > threshold ? Optional.of(ExitSignal.atNextOpen("RSI_OVERBOUGHT")) : Optional.empty());
    }

    /** Sell next open when close drops below its SMA. */
    public static ExitStrategy closeBelowSma(int period) {
        return ExitStrategy.of("close<SMA(" + period + ")", (s, i, p) ->
                s.close(i) < sma(s, period, i) ? Optional.of(ExitSignal.atNextOpen("BELOW_SMA")) : Optional.empty());
    }

    private static String pct(double fraction) {
        return Parameters.format(Math.round(fraction * 10000) / 100.0) + "%";
    }
}
