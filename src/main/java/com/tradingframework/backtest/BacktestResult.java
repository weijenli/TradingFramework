package com.tradingframework.backtest;

import com.tradingframework.strategy.TradingStrategy;

import java.util.Comparator;
import java.util.List;

/** Trades plus summary statistics: win rate, profit, drawdown, profit factor, ... */
public class BacktestResult {

    public static final Comparator<BacktestResult> BY_TOTAL_PROFIT = Comparator.comparingDouble(BacktestResult::totalProfit);
    public static final Comparator<BacktestResult> BY_WIN_RATE = Comparator.comparingDouble(BacktestResult::winRate);
    public static final Comparator<BacktestResult> BY_PROFIT_FACTOR = Comparator.comparingDouble(BacktestResult::profitFactor);
    public static final Comparator<BacktestResult> BY_EXPECTANCY = Comparator.comparingDouble(BacktestResult::expectancy);

    private final TradingStrategy strategy;
    private final BacktestConfig config;
    private final List<Trade> trades;

    private final int wins;
    private final double totalProfit;
    private final double grossWins;
    private final double grossLosses;
    private final double avgReturnPct;
    private final double avgHoldingDays;
    private final double maxDrawdownPct;

    public BacktestResult(TradingStrategy strategy, BacktestConfig config, List<Trade> trades) {
        this.strategy = strategy;
        this.config = config;
        this.trades = List.copyOf(trades);

        int w = 0;
        double total = 0, gw = 0, gl = 0, sumRet = 0, sumHold = 0;
        for (Trade t : this.trades) {
            double p = t.profit();
            total += p;
            if (t.isWin()) { w++; gw += p; } else { gl += -p; }
            sumRet += t.returnPct();
            sumHold += t.holdingDays();
        }
        int n = this.trades.size();
        this.wins = w;
        this.totalProfit = total;
        this.grossWins = gw;
        this.grossLosses = gl;
        this.avgReturnPct = n == 0 ? 0 : sumRet / n;
        this.avgHoldingDays = n == 0 ? 0 : sumHold / n;
        this.maxDrawdownPct = computeMaxDrawdown(config.initialCapital(), this.trades);
    }

    /** Peak-to-trough decline of realised equity (trades ordered by exit date). */
    private static double computeMaxDrawdown(double initial, List<Trade> trades) {
        double equity = initial, peak = initial, maxDd = 0;
        for (Trade t : trades) {
            equity += t.profit();
            peak = Math.max(peak, equity);
            maxDd = Math.max(maxDd, (peak - equity) / peak);
        }
        return maxDd;
    }

    public TradingStrategy strategy() { return strategy; }
    public BacktestConfig config() { return config; }
    public List<Trade> trades() { return trades; }

    public int totalTrades() { return trades.size(); }
    public int winningTrades() { return wins; }
    public int losingTrades() { return trades.size() - wins; }

    /** Fraction of trades with positive net profit (0..1). */
    public double winRate() { return trades.isEmpty() ? 0 : (double) wins / trades.size(); }

    /** Net profit in currency after commissions and slippage. */
    public double totalProfit() { return totalProfit; }

    /** Total profit relative to initial capital. */
    public double totalReturnPct() { return totalProfit / config.initialCapital(); }

    public double finalEquity() { return config.initialCapital() + totalProfit; }

    public double averageReturnPct() { return avgReturnPct; }

    public double averageWin() { return wins == 0 ? 0 : grossWins / wins; }

    public double averageLoss() { return losingTrades() == 0 ? 0 : -grossLosses / losingTrades(); }

    /** Gross wins / gross losses; infinite when there are wins but no losses. */
    public double profitFactor() {
        if (grossLosses == 0) return grossWins > 0 ? Double.POSITIVE_INFINITY : 0;
        return grossWins / grossLosses;
    }

    /** Average net profit per trade. */
    public double expectancy() { return trades.isEmpty() ? 0 : totalProfit / trades.size(); }

    public double maxDrawdownPct() { return maxDrawdownPct; }

    public double averageHoldingDays() { return avgHoldingDays; }

    public String summary() {
        return String.format("""
                Strategy        : %s
                Rules           : %s
                Trades          : %d (won %d, lost %d)
                Win rate        : %.1f%%
                Total profit    : %,.2f (%.2f%% of %,.0f)
                Avg return/trade: %.2f%%
                Avg win / loss  : %,.2f / %,.2f
                Profit factor   : %.2f
                Expectancy      : %,.2f per trade
                Max drawdown    : %.2f%%
                Avg holding     : %.1f days""",
                strategy.label(), strategy.rulesDescription(),
                totalTrades(), winningTrades(), losingTrades(),
                winRate() * 100,
                totalProfit(), totalReturnPct() * 100, config.initialCapital(),
                averageReturnPct() * 100,
                averageWin(), averageLoss(),
                profitFactor(),
                expectancy(),
                maxDrawdownPct() * 100,
                averageHoldingDays());
    }

    @Override
    public String toString() {
        return String.format("%s trades=%d winRate=%.1f%% profit=%.2f", strategy.label(), totalTrades(), winRate() * 100, totalProfit);
    }
}
