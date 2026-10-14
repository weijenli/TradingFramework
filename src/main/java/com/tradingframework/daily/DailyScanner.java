package com.tradingframework.daily;

import com.tradingframework.data.StockDataService;
import com.tradingframework.model.BarSeries;
import com.tradingframework.strategy.TradingStrategy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * End-of-day scanner: optionally refreshes each symbol's data, then evaluates every strategy's
 * pick + entry rules on the latest bar. Each resulting {@link StockPick} records which strategy
 * produced it.
 */
public class DailyScanner {

    private static final System.Logger LOG = System.getLogger(DailyScanner.class.getName());

    private final StockDataService dataService;
    private final List<TradingStrategy> strategies;
    private final int historyDays;
    private final int maxStalenessDays;

    /**
     * @param historyDays      calendar days of history loaded for indicators (e.g. 400 for a 200-day SMA)
     * @param maxStalenessDays skip symbols whose latest bar is older than this (delisted / failed download)
     */
    public DailyScanner(StockDataService dataService, List<TradingStrategy> strategies, int historyDays, int maxStalenessDays) {
        this.dataService = dataService;
        this.strategies = List.copyOf(strategies);
        this.historyDays = historyDays;
        this.maxStalenessDays = maxStalenessDays;
    }

    public DailyScanResult scan(Collection<String> universe, boolean refreshData) {
        return scan(universe, refreshData, LocalDate.now());
    }

    public DailyScanResult scan(Collection<String> universe, boolean refreshData, LocalDate asOf) {
        List<StockPick> picks = new ArrayList<>();
        Map<String, String> errors = new TreeMap<>();

        for (String symbol : universe) {
            try {
                if (refreshData) dataService.update(symbol);
                BarSeries series = dataService.loadSeries(symbol, asOf.minusDays(historyDays), asOf);
                if (series.isEmpty()) {
                    errors.put(symbol, "no data");
                    continue;
                }
                if (series.last().getDate().isBefore(asOf.minusDays(maxStalenessDays))) {
                    errors.put(symbol, "stale data, last bar " + series.last().getDate());
                    continue;
                }
                picks.addAll(evaluate(series));
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Scan failed for " + symbol, e);
                errors.put(symbol, e.getMessage());
            }
        }
        return new DailyScanResult(asOf, picks, errors);
    }

    /** Strategies whose entry fires on the last bar of {@code series}. */
    public List<StockPick> evaluate(BarSeries series) {
        int i = series.lastIndex();
        List<StockPick> out = new ArrayList<>();
        for (TradingStrategy strategy : strategies) {
            if (strategy.isEntrySignal(series, i)) {
                out.add(new StockPick(series.date(i), series.symbol(), series.close(i), series.volume(i),
                        strategy.name(), strategy.label(), strategy.rulesDescription()));
            }
        }
        return out;
    }
}
