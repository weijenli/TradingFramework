package com.tradingframework.strategy;

/**
 * Result of an exit rule. Either fill at a specific price during the current bar (stops, targets,
 * time exits at the close) or, when {@code price} is NaN, at the next bar's open.
 */
public record ExitSignal(String reason, double price) {

    public static ExitSignal atNextOpen(String reason) {
        return new ExitSignal(reason, Double.NaN);
    }

    public static ExitSignal atPrice(String reason, double price) {
        return new ExitSignal(reason, price);
    }

    public boolean isIntraday() {
        return !Double.isNaN(price);
    }
}
