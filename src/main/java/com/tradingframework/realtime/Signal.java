package com.tradingframework.realtime;

import java.time.Instant;

/** An alert produced by an {@link AlertStrategy}. */
public record Signal(Instant time, String symbol, SignalType type, String strategy, double price, String message) {

    public enum SignalType { BUY, SELL, ALERT }

    public String format() {
        return String.format("[%s] %-5s %-6s @ %.2f  %s — %s", time, type, symbol, price, strategy, message);
    }
}
