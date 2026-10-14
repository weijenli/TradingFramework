package com.tradingframework.backtest;

import com.tradingframework.strategy.Parameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameter values to test. {@link #combinations()} returns the Cartesian product, e.g.
 * <pre>
 * new ParameterGrid().values("fast", 5, 10, 20).range("slow", 30, 100, 10)
 * </pre>
 */
public class ParameterGrid {

    private final Map<String, List<Double>> axes = new LinkedHashMap<>();

    public ParameterGrid values(String name, double... values) {
        List<Double> list = new ArrayList<>();
        for (double v : values) list.add(v);
        axes.put(name, list);
        return this;
    }

    /** Inclusive range {@code start, start+step, ... <= end}. */
    public ParameterGrid range(String name, double start, double end, double step) {
        if (step <= 0) throw new IllegalArgumentException("step must be > 0");
        List<Double> list = new ArrayList<>();
        int count = (int) Math.floor((end - start) / step + 1e-9);
        for (int k = 0; k <= count; k++) list.add(Math.round((start + k * step) * 1e9) / 1e9);
        axes.put(name, list);
        return this;
    }

    public List<Parameters> combinations() {
        List<Map<String, Double>> combos = new ArrayList<>();
        combos.add(new LinkedHashMap<>());
        for (Map.Entry<String, List<Double>> axis : axes.entrySet()) {
            List<Map<String, Double>> next = new ArrayList<>();
            for (Map<String, Double> partial : combos) {
                for (Double v : axis.getValue()) {
                    Map<String, Double> m = new LinkedHashMap<>(partial);
                    m.put(axis.getKey(), v);
                    next.add(m);
                }
            }
            combos = next;
        }
        return combos.stream().map(Parameters::of).toList();
    }

    public int size() {
        return axes.values().stream().mapToInt(List::size).reduce(1, (a, b) -> a * b);
    }
}
