package com.tradingframework.backtest;

import com.tradingframework.model.BarSeries;
import com.tradingframework.strategy.ExitSignal;
import com.tradingframework.strategy.Position;
import com.tradingframework.strategy.TradingStrategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Event-driven daily backtester (long only, one position per symbol at a time).
 * <p>
 * For every bar: (1) pending orders from yesterday's close fill at today's open; (2) the open
 * position is checked against the exit rule — intraday exits fill on this bar, others become a
 * pending order for tomorrow's open; (3) if flat, pick + entry rules are evaluated at the close and
 * a buy fills at the next open. Signals therefore never use data they could not have seen.
 * <p>
 * Each trade is sized at {@link BacktestConfig#capitalPerTrade()}; symbols are simulated
 * independently and the combined trade list is used for statistics.
 */
public class BacktestEngine {

    public BacktestResult run(TradingStrategy strategy, Collection<BarSeries> universe, BacktestConfig config) {
        List<Trade> trades = new ArrayList<>();
        for (BarSeries series : universe) {
            trades.addAll(runSymbol(strategy, series, config));
        }
        trades.sort(Comparator.comparing(Trade::exitDate).thenComparing(Trade::symbol));
        return new BacktestResult(strategy, config, trades);
    }

    public List<Trade> runSymbol(TradingStrategy strategy, BarSeries s, BacktestConfig cfg) {
        List<Trade> trades = new ArrayList<>();
        if (s.isEmpty()) return trades;
        int end = cfg.to() == null ? s.lastIndex() : s.indexOnOrBefore(cfg.to());
        double slip = cfg.slippagePct();

        Position pos = null;
        boolean pendingEntry = false;
        ExitSignal pendingExit = null;

        for (int i = 0; i <= end; i++) {
            // 1. fill orders placed at the previous close
            if (pendingExit != null && pos != null) {
                trades.add(close(strategy, s, pos, i, s.open(i) * (1 - slip), pendingExit.reason(), cfg));
                pos = null;
            }
            pendingExit = null;
            if (pendingEntry && pos == null) {
                double price = s.open(i) * (1 + slip);
                long shares = (long) Math.floor(cfg.capitalPerTrade() / price);
                if (shares > 0) pos = new Position(s.symbol(), i, s.date(i), price, shares);
            }
            pendingEntry = false;

            // 2. manage the open position
            if (pos != null) {
                Optional<ExitSignal> exit = strategy.exit().check(s, i, pos);
                if (exit.isPresent() && exit.get().isIntraday()) {
                    trades.add(close(strategy, s, pos, i, exit.get().price() * (1 - slip), exit.get().reason(), cfg));
                    pos = null;
                } else if (exit.isPresent()) {
                    pendingExit = exit.get();
                } else {
                    pos.update(s.get(i));
                }
            }

            // 3. look for a new entry at the close
            boolean inWindow = cfg.from() == null || !s.date(i).isBefore(cfg.from());
            if (pos == null && i < end && inWindow && strategy.isEntrySignal(s, i)) {
                pendingEntry = true;
            }
        }

        if (pos != null) {
            String reason = pendingExit != null ? pendingExit.reason() : "END_OF_TEST";
            trades.add(close(strategy, s, pos, end, s.close(end) * (1 - slip), reason, cfg));
        }
        return trades;
    }

    private static Trade close(TradingStrategy strategy, BarSeries s, Position pos, int i, double price,
                               String reason, BacktestConfig cfg) {
        return new Trade(s.symbol(), strategy.label(), pos.entryDate(), pos.entryPrice(), s.date(i), price,
                pos.shares(), 2 * cfg.commissionPerOrder(), reason);
    }
}
