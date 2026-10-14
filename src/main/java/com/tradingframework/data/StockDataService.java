package com.tradingframework.data;

import com.tradingframework.model.BarSeries;
import com.tradingframework.model.StockBar;
import com.tradingframework.storage.BarRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Facade over the data layer: downloads from the configured sources (falling back to the next one
 * on failure), parses, and saves to every repository. The first repository is the primary one
 * used for reads.
 */
public class StockDataService {

    private static final System.Logger LOG = System.getLogger(StockDataService.class.getName());

    private final List<PriceDataSource> sources;
    private final HttpCsvDownloader downloader;
    private final CsvBarParser parser;
    private final List<BarRepository> repositories;
    private final LocalDate defaultHistoryStart;

    public StockDataService(List<PriceDataSource> sources, HttpCsvDownloader downloader, CsvBarParser parser,
                            List<BarRepository> repositories, LocalDate defaultHistoryStart) {
        if (repositories.isEmpty()) throw new IllegalArgumentException("At least one repository is required");
        this.sources = List.copyOf(sources);
        this.downloader = downloader;
        this.parser = parser;
        this.repositories = List.copyOf(repositories);
        this.defaultHistoryStart = defaultHistoryStart;
    }

    /** Downloads bars in [from, to] from the first source that succeeds. */
    public List<StockBar> download(String symbol, LocalDate from, LocalDate to) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        List<String> failures = new ArrayList<>();
        for (PriceDataSource source : sources) {
            if (!source.isConfigured()) continue;
            try {
                String csv = downloader.download(source.buildUri(sym, from, to), source.name(), sym);
                List<StockBar> bars = parser.parse(sym, csv).stream()
                        .filter(b -> !b.getDate().isBefore(from) && !b.getDate().isAfter(to))
                        .toList();
                LOG.log(System.Logger.Level.INFO, "{0}: {1} bars from {2}", sym, bars.size(), source.name());
                return bars;
            } catch (RuntimeException e) {
                failures.add(source.name() + ": " + e.getMessage());
                LOG.log(System.Logger.Level.WARNING, "{0} failed for {1}: {2}", source.name(), sym, e.getMessage());
            }
        }
        throw new DataDownloadException("All sources failed for " + sym + " " + failures);
    }

    /**
     * Incrementally updates a symbol: downloads from the day after the last stored bar (or from the
     * default history start) to today and saves to all repositories. Returns the number of new bars.
     */
    public int update(String symbol) {
        LocalDate today = LocalDate.now();
        LocalDate from = primary().lastDate(symbol).map(d -> d.plusDays(1)).orElse(defaultHistoryStart);
        if (from.isAfter(today)) return 0;
        List<StockBar> bars = download(symbol, from, today);
        save(bars);
        return bars.size();
    }


    public void save(List<StockBar> bars) {
        for (BarRepository repo : repositories) repo.saveAll(bars);
    }

    public BarSeries loadSeries(String symbol, LocalDate from, LocalDate to) {
        String sym = symbol.toUpperCase(Locale.ROOT);
        return new BarSeries(sym, primary().find(sym, from, to));
    }

    public BarSeries loadSeries(String symbol) {
        return loadSeries(symbol, null, null);
    }

    public Optional<LocalDate> lastDate(String symbol) {
        return primary().lastDate(symbol);
    }

    public BarRepository primary() {
        return repositories.get(0);
    }
}
