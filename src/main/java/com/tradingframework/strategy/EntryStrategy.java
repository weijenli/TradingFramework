package com.tradingframework.strategy;

import com.tradingframework.model.BarSeries;

import java.util.function.BiPredicate;

/**
 * Entry rule evaluated at the close of bar {@code index}. A true result means "buy"; the backtest
 * fills at the next bar's open so no future data is used.
 */
public interface EntryStrategy {

    String name();

    boolean shouldEnter(BarSeries series, int index);

    static EntryStrategy of(String name, BiPredicate<BarSeries, Integer> rule) {
        return new EntryStrategy() {
            @Override public String name() { return name; }
            @Override public boolean shouldEnter(BarSeries s, int i) { return rule.test(s, i); }
            @Override public String toString() { return name; }
        };
    }

    default EntryStrategy and(EntryStrategy other) {
        EntryStrategy self = this;
        return of(name() + " AND " + other.name(), (s, i) -> self.shouldEnter(s, i) && other.shouldEnter(s, i));
    }

    default EntryStrategy or(EntryStrategy other) {
        EntryStrategy self = this;
        return of(name() + " OR " + other.name(), (s, i) -> self.shouldEnter(s, i) || other.shouldEnter(s, i));
    }
}
