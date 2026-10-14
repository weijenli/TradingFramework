package com.tradingframework.data;

import com.tradingframework.model.StockBar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates realistic-looking random-walk OHLCV data (weekdays only) with trending regimes.
 * Used by tests and the offline {@code demo} command.
 */
public final class SyntheticDataGenerator {

    private SyntheticDataGenerator() {
    }

    public static List<StockBar> generate(String symbol, LocalDate start, int tradingDays, double startPrice, long seed) {
        Random rnd = new Random(seed);
        List<StockBar> bars = new ArrayList<>(tradingDays);
        double close = startPrice;
        double drift = 0;
        LocalDate date = start;
        while (bars.size() < tradingDays) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                date = date.plusDays(1);
                continue;
            }
            if (bars.size() % 60 == 0) drift = (rnd.nextDouble() - 0.45) * 0.004; // new regime every ~3 months
            double vol = 0.018;
            double open = close * (1 + rnd.nextGaussian() * vol * 0.3);
            double newClose = Math.max(1, open * (1 + drift + rnd.nextGaussian() * vol));
            double high = Math.max(open, newClose) * (1 + Math.abs(rnd.nextGaussian()) * vol * 0.5);
            double low = Math.min(open, newClose) * (1 - Math.abs(rnd.nextGaussian()) * vol * 0.5);
            long volume = (long) (1_000_000 * (0.5 + rnd.nextDouble()) * (1 + Math.abs(newClose / close - 1) * 30));
            bars.add(new StockBar(symbol, date, round(open), round(high), round(low), round(newClose), volume));
            close = newClose;
            date = date.plusDays(1);
        }
        return bars;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
