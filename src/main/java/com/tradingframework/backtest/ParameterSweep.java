package com.tradingframework.backtest;

import com.tradingframework.model.BarSeries;
import com.tradingframework.strategy.Parameters;
import com.tradingframework.strategy.TradingStrategy;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Runs a strategy over every combination in a {@link ParameterGrid} (in parallel) and ranks the
 * results. Combinations the factory rejects with {@link IllegalArgumentException} (e.g.
 * fast &gt;= slow) are skipped.
 */
public class ParameterSweep {

    private final BacktestEngine engine;

    public ParameterSweep() {
        this(new BacktestEngine());
    }

    public ParameterSweep(BacktestEngine engine) {
        this.engine = engine;
    }

    /** Results sorted best-first by {@code ranking}. */
    public List<BacktestResult> run(Function<Parameters, TradingStrategy> factory, ParameterGrid grid,
                                    Collection<BarSeries> universe, BacktestConfig config,
                                    Comparator<BacktestResult> ranking) {
        return grid.combinations().parallelStream()
                .map(params -> {
                    try {
                        return factory.apply(params);
                    } catch (IllegalArgumentException invalidCombination) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(strategy -> engine.run(strategy, universe, config))
                .sorted(ranking.reversed())
                .toList();
    }

    public List<BacktestResult> run(Function<Parameters, TradingStrategy> factory, ParameterGrid grid,
                                    Collection<BarSeries> universe, BacktestConfig config) {
        return run(factory, grid, universe, config, BacktestResult.BY_TOTAL_PROFIT);
    }
}
