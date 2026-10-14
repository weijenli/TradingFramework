package com.tradingframework.strategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable named numeric parameters for a strategy, e.g. {@code fast=10, slow=50}. */
public final class Parameters {

    private static final Parameters EMPTY = new Parameters(Map.of());

    private final Map<String, Double> values;

    private Parameters(Map<String, Double> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Parameters empty() {
        return EMPTY;
    }

    public static Parameters of(Map<String, ? extends Number> values) {
        Map<String, Double> m = new LinkedHashMap<>();
        values.forEach((k, v) -> m.put(k, v.doubleValue()));
        return new Parameters(m);
    }

    /** {@code Parameters.of("fast", 10, "slow", 50)} */
    public static Parameters of(Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("Expected key/value pairs");
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], ((Number) keyValues[i + 1]).doubleValue());
        }
        return new Parameters(m);
    }

    public double get(String name, double defaultValue) {
        return values.getOrDefault(name, defaultValue);
    }

    public int getInt(String name, int defaultValue) {
        return (int) Math.round(get(name, defaultValue));
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public Parameters with(String name, double value) {
        Map<String, Double> m = new LinkedHashMap<>(values);
        m.put(name, value);
        return new Parameters(m);
    }

    /** These parameters, with values from {@code overrides} taking precedence. */
    public Parameters overriddenBy(Parameters overrides) {
        Map<String, Double> m = new LinkedHashMap<>(values);
        m.putAll(overrides.values);
        return new Parameters(m);
    }

    public Map<String, Double> asMap() {
        return values;
    }

    @Override
    public String toString() {
        return values.entrySet().stream()
                .map(e -> e.getKey() + "=" + format(e.getValue()))
                .collect(Collectors.joining(", "));
    }

    static String format(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : Double.toString(v);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Parameters p && values.equals(p.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }
}
