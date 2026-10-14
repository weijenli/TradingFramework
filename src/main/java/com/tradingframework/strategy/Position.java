package com.tradingframework.strategy;

import com.tradingframework.model.StockBar;

import java.time.LocalDate;

/** An open long position, as seen by exit rules. */
public class Position {

    private final String symbol;
    private final int entryIndex;
    private final LocalDate entryDate;
    private final double entryPrice;
    private final long shares;
    private double highestHigh;
    private double lowestLow;

    public Position(String symbol, int entryIndex, LocalDate entryDate, double entryPrice, long shares) {
        this.symbol = symbol;
        this.entryIndex = entryIndex;
        this.entryDate = entryDate;
        this.entryPrice = entryPrice;
        this.shares = shares;
        this.highestHigh = entryPrice;
        this.lowestLow = entryPrice;
    }

    /** Called after exit rules have been checked for a bar the position survived. */
    public void update(StockBar bar) {
        highestHigh = Math.max(highestHigh, bar.getHigh());
        lowestLow = Math.min(lowestLow, bar.getLow());
    }

    public String symbol() { return symbol; }
    public int entryIndex() { return entryIndex; }
    public LocalDate entryDate() { return entryDate; }
    public double entryPrice() { return entryPrice; }
    public long shares() { return shares; }
    /** Highest high since entry, excluding the bar currently being evaluated. */
    public double highestHigh() { return highestHigh; }
    public double lowestLow() { return lowestLow; }

    public int barsHeld(int index) {
        return index - entryIndex;
    }
}
