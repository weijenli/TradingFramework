package com.tradingframework.realtime;

import com.tradingframework.model.BarSeries;
import com.tradingframework.model.StockBar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Folds real-time quotes into a "live" daily bar per symbol and appends it to the symbol's
 * end-of-day history, so the same daily strategies/indicators can be evaluated intraday
 * ("what if the day closed now"). When the trading date changes, the finished live bar moves
 * into history.
 */
public class LiveSeriesTracker {

    private final ZoneId exchangeZone;
    private final Map<String, BarSeries> history = new ConcurrentHashMap<>();
    private final Map<String, StockBar> liveBars = new ConcurrentHashMap<>();

    public LiveSeriesTracker(ZoneId exchangeZone) {
        this.exchangeZone = exchangeZone;
    }

    /** Sets the completed daily history (bars before today) for a symbol. */
    public void setHistory(BarSeries series) {
        history.put(series.symbol().toUpperCase(Locale.ROOT), series);
    }

    /** Updates the live bar with {@code q} and returns history + live bar. */
    public synchronized BarSeries onQuote(Quote q) {
        String sym = q.symbol().toUpperCase(Locale.ROOT);
        LocalDate day = q.timestamp().atZone(exchangeZone).toLocalDate();
        BarSeries hist = history.getOrDefault(sym, new BarSeries(sym, List.of()));
        // Drop a stored bar for today (e.g. history refreshed after the close) so the live bar replaces it.
        if (!hist.isEmpty() && !hist.last().getDate().isBefore(day)) {
            hist = new BarSeries(sym, hist.bars().stream().filter(b -> b.getDate().isBefore(day)).toList());
            history.put(sym, hist);
        }

        StockBar live = liveBars.get(sym);
        if (live != null && !live.getDate().equals(day)) {
            hist = hist.withBar(live);
            history.put(sym, hist);
            live = null;
        }
        double p = q.price();
        live = live == null
                ? new StockBar(sym, day, p, p, p, p, q.volume())
                : new StockBar(sym, day, live.getOpen(), Math.max(live.getHigh(), p), Math.min(live.getLow(), p), p,
                        live.getVolume() + q.volume());
        liveBars.put(sym, live);
        return hist.withBar(live);
    }
}
