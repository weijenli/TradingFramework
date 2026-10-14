package com.tradingframework.backtest;

import java.time.LocalDate;

/**
 * Backtest settings.
 *
 * @param initialCapital     account size used for position sizing and return calculations
 * @param positionSizePct    fraction of initial capital put into each trade (0.10 = 10%)
 * @param commissionPerOrder flat commission charged on each buy and each sell
 * @param slippagePct        adverse fill slippage per order (0.001 = 0.1%)
 * @param from               first date on which new trades may be entered (null = start of data);
 *                           earlier data is still used to warm up indicators
 * @param to                 last date simulated (null = end of data); open trades close here
 */
public record BacktestConfig(double initialCapital, double positionSizePct, double commissionPerOrder,
                             double slippagePct, LocalDate from, LocalDate to) {

    public static BacktestConfig defaults() {
        return new BacktestConfig(100_000, 0.10, 1.0, 0.0005, null, null);
    }

    public BacktestConfig withDateRange(LocalDate from, LocalDate to) {
        return new BacktestConfig(initialCapital, positionSizePct, commissionPerOrder, slippagePct, from, to);
    }

    public BacktestConfig withCosts(double commissionPerOrder, double slippagePct) {
        return new BacktestConfig(initialCapital, positionSizePct, commissionPerOrder, slippagePct, from, to);
    }

    public BacktestConfig withSizing(double initialCapital, double positionSizePct) {
        return new BacktestConfig(initialCapital, positionSizePct, commissionPerOrder, slippagePct, from, to);
    }

    public double capitalPerTrade() {
        return initialCapital * positionSizePct;
    }
}
