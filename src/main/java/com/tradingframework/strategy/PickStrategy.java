package com.tradingframework.strategy;

import com.tradingframework.model.BarSeries;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Stock-picking (screening) rule: decides whether a stock is a candidate at bar {@code index},
 * e.g. liquid enough, above its 200-day average. Entry rules are only checked for candidates.
 */
public interface PickStrategy {

    String name();

    boolean matches(BarSeries series, int index);

    static PickStrategy of(String name, BiPredicate<BarSeries, Integer> rule) {
        return new PickStrategy() {
            @Override public String name() { return name; }
            @Override public boolean matches(BarSeries s, int i) { return rule.test(s, i); }
            @Override public String toString() { return name; }
        };
    }

    /** Accepts every stock. */
    static PickStrategy any() {
        return of("ANY", (s, i) -> true);
    }

    /** Matches only when every rule matches. */
    static PickStrategy all(PickStrategy... rules) {
        List<PickStrategy> list = Arrays.asList(rules);
        String name = list.stream().map(PickStrategy::name).collect(Collectors.joining(" AND "));
        return of(name, (s, i) -> list.stream().allMatch(r -> r.matches(s, i)));
    }
}
