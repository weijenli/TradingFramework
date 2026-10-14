package com.tradingframework.strategy;

import static com.tradingframework.indicator.Indicators.averageVolume;
import static com.tradingframework.indicator.Indicators.highestHigh;
import static com.tradingframework.indicator.Indicators.rsi;
import static com.tradingframework.indicator.Indicators.sma;

/** Built-in entry rules. All are evaluated with data up to and including bar {@code i}. */
public final class Entries {

    private Entries() {
    }

    /** Fast SMA crosses above slow SMA ("golden cross"). */
    public static EntryStrategy movingAverageCross(int fast, int slow) {
        return EntryStrategy.of("SMA(" + fast + ") crosses above SMA(" + slow + ")", (s, i) ->
                i >= 1
                        && sma(s, fast, i) > sma(s, slow, i)
                        && sma(s, fast, i - 1) <= sma(s, slow, i - 1));
    }

    /** RSI rises back above the oversold threshold (mean-reversion buy). */
    public static EntryStrategy rsiCrossesAbove(int period, double threshold) {
        return EntryStrategy.of("RSI(" + period + ") crosses above " + Parameters.format(threshold), (s, i) ->
                i >= 1 && rsi(s, period, i - 1) < threshold && rsi(s, period, i) >= threshold);
    }

    /** RSI is below a threshold. */
    public static EntryStrategy rsiBelow(int period, double threshold) {
        return EntryStrategy.of("RSI(" + period + ") < " + Parameters.format(threshold),
                (s, i) -> rsi(s, period, i) < threshold);
    }

    /** Close breaks above the prior {@code lookback}-day high on volume above {@code volumeMultiple} x average. */
    public static EntryStrategy breakout(int lookback, double volumeMultiple, int volumePeriod) {
        return EntryStrategy.of(lookback + "-day breakout, vol>" + Parameters.format(volumeMultiple) + "x avg", (s, i) ->
                i >= 1
                        && s.close(i) > highestHigh(s, lookback, i)
                        && s.volume(i) >= volumeMultiple * averageVolume(s, volumePeriod, i - 1));
    }
}
