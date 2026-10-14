package com.tradingframework.strategy;

import static com.tradingframework.indicator.Indicators.averageVolume;
import static com.tradingframework.indicator.Indicators.sma;

/** Built-in stock-picking rules. */
public final class Picks {

    private Picks() {
    }

    public static PickStrategy minPrice(double minPrice) {
        return PickStrategy.of("price>=" + Parameters.format(minPrice), (s, i) -> s.close(i) >= minPrice);
    }

    public static PickStrategy minAverageVolume(double minVolume, int period) {
        return PickStrategy.of("avgVol(" + period + ")>=" + Parameters.format(minVolume),
                (s, i) -> averageVolume(s, period, i) >= minVolume);
    }

    /** Uptrend filter: close above its simple moving average. */
    public static PickStrategy aboveSma(int period) {
        return PickStrategy.of("close>SMA(" + period + ")", (s, i) -> s.close(i) > sma(s, period, i));
    }

    /** Pullback filter: close below its simple moving average. */
    public static PickStrategy belowSma(int period) {
        return PickStrategy.of("close<SMA(" + period + ")", (s, i) -> s.close(i) < sma(s, period, i));
    }
}
