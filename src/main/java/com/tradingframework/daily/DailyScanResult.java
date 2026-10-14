package com.tradingframework.daily;

import java.io.PrintStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Output of one daily scan: the picks, plus symbols that could not be processed. */
public record DailyScanResult(LocalDate runDate, List<StockPick> picks, Map<String, String> errors) {

    /** Picks grouped by symbol (alphabetical); a symbol may be picked by several strategies. */
    public Map<String, List<StockPick>> picksBySymbol() {
        return picks.stream().collect(Collectors.groupingBy(StockPick::symbol, TreeMap::new, Collectors.toList()));
    }

    public void print(PrintStream out) {
        out.println("=== Daily stock picks (run " + runDate + ") ===");
        if (picks.isEmpty()) {
            out.println("No stocks met any strategy today.");
        }
        picksBySymbol().forEach((symbol, list) -> {
            StockPick first = list.get(0);
            out.printf("%-8s close %10.2f  vol %,13d  as of %s%n", symbol, first.close(), first.volume(), first.date());
            for (StockPick p : list) {
                out.printf("    strategy: %s%n        %s%n", p.strategyLabel(), p.rules());
            }
        });
        if (!errors.isEmpty()) {
            out.println("Skipped " + errors.size() + " symbol(s):");
            errors.forEach((sym, err) -> out.println("    " + sym + ": " + err));
        }
    }
}
