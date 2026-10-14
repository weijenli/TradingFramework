package com.tradingframework.daily;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes each scan to {@code <dir>/picks-YYYY-MM-DD.csv}. */
public class PickReportWriter {

    private final Path dir;

    public PickReportWriter(Path dir) {
        this.dir = dir;
    }

    public Path write(DailyScanResult result) {
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("picks-" + result.runDate() + ".csv");
            try (BufferedWriter w = Files.newBufferedWriter(file)) {
                w.write("date,symbol,close,volume,strategy,parameters,rules");
                w.newLine();
                for (StockPick p : result.picks()) {
                    w.write(String.join(",", p.date().toString(), p.symbol(), String.format("%.4f", p.close()),
                            Double.toString(p.volume()), p.strategyName(), quote(p.strategyLabel()), quote(p.rules())));
                    w.newLine();
                }
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String quote(String s) {
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
