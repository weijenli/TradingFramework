package com.tradingframework.indicator;

import com.tradingframework.model.BarSeries;

import java.util.Arrays;

/**
 * Technical indicators over a {@link BarSeries}. Full arrays are computed once and cached on the
 * series; point lookups return {@link Double#NaN} where there is not enough history, so rules
 * comparing NaN values evaluate to false naturally.
 */
public final class Indicators {

    private Indicators() {
    }

    // ---- simple moving average of close ----

    public static double sma(BarSeries s, int period, int i) {
        return at(smaSeries(s, period), i);
    }

    public static double[] smaSeries(BarSeries s, int period) {
        return s.cached("SMA:" + period, () -> rollingMean(closes(s), period));
    }

    // ---- exponential moving average of close ----

    public static double ema(BarSeries s, int period, int i) {
        return at(s.cached("EMA:" + period, () -> emaOf(closes(s), period)), i);
    }

    // ---- average volume ----

    public static double averageVolume(BarSeries s, int period, int i) {
        return at(s.cached("AVGVOL:" + period, () -> {
            double[] v = new double[s.size()];
            for (int k = 0; k < v.length; k++) v[k] = s.volume(k);
            return rollingMean(v, period);
        }), i);
    }

    // ---- RSI (Wilder) ----

    public static double rsi(BarSeries s, int period, int i) {
        return at(s.cached("RSI:" + period, () -> rsiOf(closes(s), period)), i);
    }

    // ---- ATR (Wilder) ----

    public static double atr(BarSeries s, int period, int i) {
        return at(s.cached("ATR:" + period, () -> atrOf(s, period)), i);
    }

    // ---- channel helpers (exclude the current bar) ----

    /** Highest high of the {@code lookback} bars before {@code i}. */
    public static double highestHigh(BarSeries s, int lookback, int i) {
        if (i - lookback < 0) return Double.NaN;
        double max = Double.NEGATIVE_INFINITY;
        for (int k = i - lookback; k < i; k++) max = Math.max(max, s.high(k));
        return max;
    }

    /** Lowest low of the {@code lookback} bars before {@code i}. */
    public static double lowestLow(BarSeries s, int lookback, int i) {
        if (i - lookback < 0) return Double.NaN;
        double min = Double.POSITIVE_INFINITY;
        for (int k = i - lookback; k < i; k++) min = Math.min(min, s.low(k));
        return min;
    }

    /** Fractional change of close over {@code n} bars, e.g. 0.05 = +5%. */
    public static double percentChange(BarSeries s, int n, int i) {
        if (i - n < 0) return Double.NaN;
        return s.close(i) / s.close(i - n) - 1;
    }

    // ---- array implementations ----

    static double[] closes(BarSeries s) {
        double[] c = new double[s.size()];
        for (int k = 0; k < c.length; k++) c[k] = s.close(k);
        return c;
    }

    static double[] rollingMean(double[] x, int period) {
        double[] out = nanArray(x.length);
        double sum = 0;
        for (int k = 0; k < x.length; k++) {
            sum += x[k];
            if (k >= period) sum -= x[k - period];
            if (k >= period - 1) out[k] = sum / period;
        }
        return out;
    }

    static double[] emaOf(double[] x, int period) {
        double[] out = nanArray(x.length);
        if (x.length < period) return out;
        double alpha = 2.0 / (period + 1);
        double seed = 0;
        for (int k = 0; k < period; k++) seed += x[k];
        out[period - 1] = seed / period;
        for (int k = period; k < x.length; k++) out[k] = alpha * x[k] + (1 - alpha) * out[k - 1];
        return out;
    }

    static double[] rsiOf(double[] c, int period) {
        double[] out = nanArray(c.length);
        if (c.length <= period) return out;
        double gain = 0, loss = 0;
        for (int k = 1; k <= period; k++) {
            double d = c[k] - c[k - 1];
            if (d > 0) gain += d; else loss -= d;
        }
        gain /= period;
        loss /= period;
        out[period] = rsiValue(gain, loss);
        for (int k = period + 1; k < c.length; k++) {
            double d = c[k] - c[k - 1];
            gain = (gain * (period - 1) + Math.max(d, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-d, 0)) / period;
            out[k] = rsiValue(gain, loss);
        }
        return out;
    }

    private static double rsiValue(double avgGain, double avgLoss) {
        if (avgLoss == 0) return avgGain == 0 ? 50 : 100;
        return 100 - 100 / (1 + avgGain / avgLoss);
    }

    static double[] atrOf(BarSeries s, int period) {
        int n = s.size();
        double[] out = nanArray(n);
        if (n < period) return out;
        double[] tr = new double[n];
        for (int k = 0; k < n; k++) {
            double hl = s.high(k) - s.low(k);
            tr[k] = k == 0 ? hl : Math.max(hl, Math.max(Math.abs(s.high(k) - s.close(k - 1)), Math.abs(s.low(k) - s.close(k - 1))));
        }
        double sum = 0;
        for (int k = 0; k < period; k++) sum += tr[k];
        out[period - 1] = sum / period;
        for (int k = period; k < n; k++) out[k] = (out[k - 1] * (period - 1) + tr[k]) / period;
        return out;
    }

    private static double[] nanArray(int n) {
        double[] a = new double[n];
        Arrays.fill(a, Double.NaN);
        return a;
    }

    private static double at(double[] a, int i) {
        return i >= 0 && i < a.length ? a[i] : Double.NaN;
    }
}
