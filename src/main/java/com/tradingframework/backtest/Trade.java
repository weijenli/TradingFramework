package com.tradingframework.backtest;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A completed round-trip trade (long only). Prices include slippage. */
public record Trade(String symbol, String strategy,
                    LocalDate entryDate, double entryPrice,
                    LocalDate exitDate, double exitPrice,
                    long shares, double commission, String exitReason) {

    public double grossProfit() {
        return (exitPrice - entryPrice) * shares;
    }

    /** Profit after commissions. */
    public double profit() {
        return grossProfit() - commission;
    }

    /** Net return on the capital committed, e.g. 0.05 = +5%. */
    public double returnPct() {
        return profit() / (entryPrice * shares);
    }

    public boolean isWin() {
        return profit() > 0;
    }

    public long holdingDays() {
        return ChronoUnit.DAYS.between(entryDate, exitDate);
    }
}
