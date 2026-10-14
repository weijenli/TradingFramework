package com.tradingframework.model;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * An ordered list of {@link StockBar}s that rejects bars whose date (formatted as yyyyMMdd) was already added.
 */
public class StockBarList implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final DateTimeFormatter YYYYMMDD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final List<StockBar> list = new ArrayList<>();
    private final Set<String> dateSet = new HashSet<>();

    /**
     * Appends {@code bar} to the end of the list unless a bar with the same date is already present.
     *
     * @return true if the bar was added
     */
    public boolean add(StockBar bar) {
        String dateString = bar.getDate().format(YYYYMMDD_FORMATTER);
        if (!dateSet.add(dateString)) return false;
        list.add(bar);
        return true;
    }

    public List<StockBar> getList() {
        return list;
    }

    public Set<String> getDateSet() {
        return dateSet;
    }

    public int size() {
        return list.size();
    }

    public void sort() {
        Collections.sort(list);
    }
}
