package com.tradingframework.backtest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Console and CSV output for backtest results. */
public final class BacktestReport {

    private BacktestReport() {
    }

    /** One row per result: trades, win rate, profit, return, profit factor, drawdown. */
    public static void printTable(List<BacktestResult> results, PrintStream out, int limit) {
        out.printf("%-4s %-70s %7s %8s %14s %9s %7s %8s%n",
                "#", "Strategy (parameters)", "Trades", "WinRate", "Profit", "Return", "PF", "MaxDD");
        int rank = 0;
        for (BacktestResult r : results) {
            if (rank >= limit) break;
            rank++;
            out.printf("%-4d %-70s %7d %7.1f%% %,14.2f %8.2f%% %7.2f %7.2f%%%n",
                    rank, truncate(r.strategy().label(), 70), r.totalTrades(), r.winRate() * 100,
                    r.totalProfit(), r.totalReturnPct() * 100, r.profitFactor(), r.maxDrawdownPct() * 100);
        }
    }

    public static void printTrades(BacktestResult result, PrintStream out, int limit) {
        out.printf("%-8s %-10s %10s %-10s %10s %8s %12s %8s  %s%n",
                "Symbol", "Entry", "Price", "Exit", "Price", "Shares", "Profit", "Return", "Reason");
        result.trades().stream().limit(limit).forEach(t ->
                out.printf("%-8s %-10s %10.2f %-10s %10.2f %8d %,12.2f %7.2f%%  %s%n",
                        t.symbol(), t.entryDate(), t.entryPrice(), t.exitDate(), t.exitPrice(),
                        t.shares(), t.profit(), t.returnPct() * 100, t.exitReason()));
        if (result.trades().size() > limit) out.println("... " + (result.trades().size() - limit) + " more trades");
    }

    public static void writeTradesCsv(BacktestResult result, Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file)) {
                w.write("symbol,strategy,entryDate,entryPrice,exitDate,exitPrice,shares,commission,profit,returnPct,exitReason");
                w.newLine();
                for (Trade t : result.trades()) {
                    w.write(String.join(",", t.symbol(), '"' + t.strategy() + '"',
                            t.entryDate().toString(), fmt(t.entryPrice()), t.exitDate().toString(), fmt(t.exitPrice()),
                            Long.toString(t.shares()), fmt(t.commission()), fmt(t.profit()),
                            String.format("%.6f", t.returnPct()), t.exitReason()));
                    w.newLine();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fmt(double v) {
        return String.format("%.4f", v);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
