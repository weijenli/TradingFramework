package com.tradingframework.storage;

import com.tradingframework.data.CsvBarParser;
import com.tradingframework.model.StockBar;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stores bars as one CSV file per symbol: {@code <dir>/<SYMBOL>.csv} with header
 * {@code symbol,date,open,high,low,close,volume,adjClose}. Saves merge with existing rows and
 * are written atomically via a temp file.
 */
public class CsvFileBarRepository implements BarRepository {

    static final String HEADER = "symbol,date,open,high,low,close,volume,adjClose";

    private final Path dir;
    private final CsvBarParser parser = new CsvBarParser();

    public CsvFileBarRepository(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void saveAll(Collection<StockBar> bars) {
        Map<String, List<StockBar>> bySymbol = bars.stream().collect(Collectors.groupingBy(StockBar::getSymbol));
        bySymbol.forEach((symbol, newBars) -> {
            TreeMap<LocalDate, StockBar> merged = new TreeMap<>();
            readFile(symbol).forEach(b -> merged.put(b.getDate(), b));
            newBars.forEach(b -> merged.put(b.getDate(), b));
            writeFile(symbol, merged.values());
        });
    }

    @Override
    public synchronized List<StockBar> find(String symbol, LocalDate from, LocalDate to) {
        return readFile(symbol).stream()
                .filter(b -> from == null || !b.getDate().isBefore(from))
                .filter(b -> to == null || !b.getDate().isAfter(to))
                .toList();
    }

    @Override
    public synchronized Optional<LocalDate> lastDate(String symbol) {
        List<StockBar> bars = readFile(symbol);
        return bars.isEmpty() ? Optional.empty() : Optional.of(bars.get(bars.size() - 1).getDate());
    }

    @Override
    public synchronized Set<String> symbols() {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".csv"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(String symbol) {
        return dir.resolve(symbol.toUpperCase(Locale.ROOT) + ".csv");
    }

    private List<StockBar> readFile(String symbol) {
        Path file = file(symbol);
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            return parser.parse(symbol.toUpperCase(Locale.ROOT), Files.newBufferedReader(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeFile(String symbol, Collection<StockBar> bars) {
        Path target = file(symbol);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp)) {
            w.write(HEADER);
            w.newLine();
            for (StockBar b : bars) {
                w.write(String.join(",", b.getSymbol(), b.getDate().toString(),
                        num(b.getOpen()), num(b.getHigh()), num(b.getLow()), num(b.getClose()),
                        Double.toString(b.getVolume()), num(b.getAdjClose())));
                w.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String num(double v) {
        return Double.toString(v);
    }
}
