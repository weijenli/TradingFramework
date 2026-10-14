package com.tradingframework.realtime;

import com.tradingframework.model.BarSeries;

/**
 * What an alert strategy sees on each evaluation.
 *
 * @param quote         the latest quote
 * @param previousPrice price at the previous evaluation of this symbol (NaN on the first one)
 * @param series        daily history with today's live bar as the last bar
 */
public record AlertContext(Quote quote, double previousPrice, BarSeries series) {

    public String symbol() {
        return quote.symbol();
    }

    public double price() {
        return quote.price();
    }

    public int lastIndex() {
        return series.lastIndex();
    }

    /** Yesterday's close, or NaN if there is no history. */
    public double previousClose() {
        return series.size() >= 2 ? series.close(series.lastIndex() - 1) : Double.NaN;
    }

    public Signal signal(Signal.SignalType type, String strategy, String message) {
        return new Signal(quote.timestamp(), quote.symbol(), type, strategy, quote.price(), message);
    }
}
