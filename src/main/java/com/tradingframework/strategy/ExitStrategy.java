package com.tradingframework.strategy;

import com.tradingframework.model.BarSeries;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Exit rule for an open {@link Position}, evaluated on each bar while the position is held. */
public interface ExitStrategy {

    String name();

    Optional<ExitSignal> check(BarSeries series, int index, Position position);

    @FunctionalInterface
    interface Rule {
        Optional<ExitSignal> check(BarSeries series, int index, Position position);
    }

    static ExitStrategy of(String name, Rule rule) {
        return new ExitStrategy() {
            @Override public String name() { return name; }
            @Override public Optional<ExitSignal> check(BarSeries s, int i, Position p) { return rule.check(s, i, p); }
            @Override public String toString() { return name; }
        };
    }

    /**
     * Combines exits. Intraday fills (stops, targets) win over next-open exits because they happen
     * first in time; among those, earlier rules in the list take precedence (put stops first to be
     * conservative when a bar touches both stop and target).
     */
    static ExitStrategy anyOf(ExitStrategy... exits) {
        List<ExitStrategy> list = Arrays.asList(exits);
        String name = list.stream().map(ExitStrategy::name).collect(Collectors.joining(" OR "));
        return of(name, (s, i, p) -> {
            ExitSignal nextOpen = null;
            for (ExitStrategy e : list) {
                Optional<ExitSignal> sig = e.check(s, i, p);
                if (sig.isEmpty()) continue;
                if (sig.get().isIntraday()) return sig;
                if (nextOpen == null) nextOpen = sig.get();
            }
            return Optional.ofNullable(nextOpen);
        });
    }
}
