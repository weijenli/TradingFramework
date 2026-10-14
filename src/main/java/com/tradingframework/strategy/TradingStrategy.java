package com.tradingframework.strategy;

import com.tradingframework.model.BarSeries;

/**
 * A complete strategy: which stocks to consider (pick), when to buy (entry) and when to sell
 * (exit), plus the parameters it was built with. Used by the backtester, the daily scanner and
 * the real-time alert engine.
 */
public record TradingStrategy(String name, Parameters parameters,
                              PickStrategy pick, EntryStrategy entry, ExitStrategy exit) {

    /** True when the stock passes the pick filter and the entry rule fires at bar {@code index}. */
    public boolean isEntrySignal(BarSeries series, int index) {
        return pick.matches(series, index) && entry.shouldEnter(series, index);
    }

    /** Name with parameters, e.g. {@code MA_CROSSOVER(fast=10, slow=50, ...)}. */
    public String label() {
        return parameters.asMap().isEmpty() ? name : name + "(" + parameters + ")";
    }

    public String rulesDescription() {
        return "pick: " + pick.name() + " | entry: " + entry.name() + " | exit: " + exit.name();
    }

    @Override
    public String toString() {
        return label();
    }
}
