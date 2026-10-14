package com.tradingframework;

import com.tradingframework.model.BarSeries;
import com.tradingframework.model.StockBar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Helpers for building small hand-crafted series in tests. */
public final class TestBars {

    public static final LocalDate START = LocalDate.of(2024, 1, 1);

    private TestBars() {
    }

    /** Bars with open=high=low=close=price, one per calendar day. */
    public static BarSeries flat(String symbol, double... closes) {
        List<StockBar> bars = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            bars.add(new StockBar(symbol, START.plusDays(i), closes[i], closes[i], closes[i], closes[i], 1_000_000));
        }
        return new BarSeries(symbol, bars);
    }

    public static StockBar bar(String symbol, int day, double open, double high, double low, double close) {
        return new StockBar(symbol, START.plusDays(day), open, high, low, close, 1_000_000);
    }
}
