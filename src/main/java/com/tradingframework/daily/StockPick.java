package com.tradingframework.daily;

import java.time.LocalDate;

/** A stock flagged by a strategy at the close of {@code date}. */
public record StockPick(LocalDate date, String symbol, double close, double volume,
                        String strategyName, String strategyLabel, String rules) {
}
