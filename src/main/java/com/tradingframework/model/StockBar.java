package com.tradingframework.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;

/**
 * One day of end-of-day price data for a single symbol.
 * <p>
 * Plain POJO (no-arg constructor, getters/setters, {@link Serializable}) so it can be written to
 * CSV files, Java-serialized, or mapped to a database row. Identity is the natural key
 * {@code (symbol, date)}.
 */
public class StockBar implements Serializable, Comparable<StockBar> {

    private static final long serialVersionUID = 1L;

    private static final Comparator<StockBar> ORDER =
            Comparator.comparing(StockBar::getDate).thenComparing(StockBar::getSymbol);

    private String symbol;
    private LocalDate date;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    /** Split/dividend adjusted close; equals {@link #close} when the source provides none. */
    private double adjClose;

    public StockBar() {
    }

    public StockBar(String symbol, LocalDate date, double open, double high, double low, double close, double volume) {
        this(symbol, date, open, high, low, close, volume, close);
    }

    public StockBar(String symbol, LocalDate date, double open, double high, double low, double close,
                    double volume, double adjClose) {
        this.symbol = symbol;
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.adjClose = adjClose;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }

    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = volume; }

    public double getAdjClose() { return adjClose; }
    public void setAdjClose(double adjClose) { this.adjClose = adjClose; }

    @Override
    public int compareTo(StockBar other) {
        return ORDER.compare(this, other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockBar that)) return false;
        return Objects.equals(symbol, that.symbol) && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, date);
    }

    @Override
    public String toString() {
        return "StockBar{" + symbol + " " + date
                + " O=" + open + " H=" + high + " L=" + low + " C=" + close
                + " V=" + volume + " adjC=" + adjClose + '}';
    }
}
