package com.tradingframework.model;

import java.io.Serializable;
import java.util.*;

public class StockBarCollection implements Serializable {
    private static final long serialVersionUID = 1L;
    Map<String, StockBarList> stockBarMap = new HashMap<>();
    Set<String> dateSet = new HashSet<>();

    public Map<String, StockBarList> getStockBarMap() {
        return stockBarMap;
    }

    public void setStockBarMap(Map<String, StockBarList> stockBarMap) {
        this.stockBarMap = stockBarMap;
    }

    public void addStockBars(List<StockBar> list) {
        if (list == null) return;

        for(StockBar bar: list) {
            addOne(bar);
        }
    }

    public void addOne(StockBar bar) {
        StockBarList barList = stockBarMap.computeIfAbsent(bar.getSymbol(), k -> new StockBarList());
        barList.add(bar);
    }

    public List<StockBar> getStock(String symbol) {
        StockBarList list = stockBarMap.get(symbol);
        if (list != null) list.sort();
        assert list != null;
        return list.getList();
    }

    public int size() {
        return stockBarMap.size();
    }
}
