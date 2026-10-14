package com.tradingframework.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Immutable, date-ordered price history of one symbol, addressed by bar index.
 * Indicator arrays computed over the series are cached, so a series can be shared across
 * many strategies and threads (e.g. a parameter sweep) without recomputing.
 */
public final class BarSeries {

    private final String symbol;
    private final List<StockBar> bars;
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    public BarSeries(String symbol, List<StockBar> bars) {
        this.symbol = symbol;
        List<StockBar> sorted = new ArrayList<>(bars);
        Collections.sort(sorted);
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getDate().equals(sorted.get(i - 1).getDate())) {
                throw new IllegalArgumentException("Duplicate bar for " + symbol + " on " + sorted.get(i).getDate());
            }
        }
        this.bars = Collections.unmodifiableList(sorted);
    }

    public String symbol() { return symbol; }
    public List<StockBar> bars() { return bars; }
    public int size() { return bars.size(); }
    public boolean isEmpty() { return bars.isEmpty(); }
    public int lastIndex() { return bars.size() - 1; }
    public StockBar get(int i) { return bars.get(i); }
    public StockBar last() { return bars.get(lastIndex()); }

    public LocalDate date(int i) { return bars.get(i).getDate(); }
    public double open(int i) { return bars.get(i).getOpen(); }
    public double high(int i) { return bars.get(i).getHigh(); }
    public double low(int i) { return bars.get(i).getLow(); }
    public double close(int i) { return bars.get(i).getClose(); }
    public double volume(int i) { return bars.get(i).getVolume(); }

    /** Index of the last bar dated on or before {@code date}, or -1 if none. */
    public int indexOnOrBefore(LocalDate date) {
        int lo = 0, hi = bars.size() - 1, result = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (!bars.get(mid).getDate().isAfter(date)) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    /**
     * Returns a new series with {@code bar} appended, or replacing the last bar when it has the same
     * date (used to fold a live intraday bar into the history).
     */
    public BarSeries withBar(StockBar bar) {
        List<StockBar> copy = new ArrayList<>(bars);
        if (!copy.isEmpty() && copy.get(copy.size() - 1).getDate().equals(bar.getDate())) {
            copy.set(copy.size() - 1, bar);
        } else if (!copy.isEmpty() && bar.getDate().isBefore(last().getDate())) {
            throw new IllegalArgumentException("Bar " + bar.getDate() + " is before end of series " + last().getDate());
        } else {
            copy.add(bar);
        }
        return new BarSeries(symbol, copy);
    }

    /** Returns the cached array for {@code key}, computing it once if absent. */
    public double[] cached(String key, Supplier<double[]> compute) {
        double[] values = cache.get(key);
        if (values == null) {
            // Not computeIfAbsent: the supplier may itself read other cached indicators.
            values = compute.get();
            double[] existing = cache.putIfAbsent(key, values);
            if (existing != null) values = existing;
        }
        return values;
    }

    @Override
    public String toString() {
        return isEmpty() ? symbol + "[empty]" : symbol + "[" + size() + " bars " + date(0) + ".." + last().getDate() + "]";
    }
}
