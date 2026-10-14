package com.tradingframework.storage;

import com.tradingframework.model.StockBar;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Persistence for {@link StockBar}s. Saving is an upsert keyed on (symbol, date). */
public interface BarRepository {

    void saveAll(Collection<StockBar> bars);

    /** Bars for {@code symbol} with from &lt;= date &lt;= to, sorted by date. Null bounds are open. */
    List<StockBar> find(String symbol, LocalDate from, LocalDate to);

    default List<StockBar> findAll(String symbol) {
        return find(symbol, null, null);
    }

    Optional<LocalDate> lastDate(String symbol);

    Set<String> symbols();
}
