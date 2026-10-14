package com.tradingframework.realtime;

import java.time.Instant;

/** A real-time trade/price update. {@code volume} is the size of this trade (shares). */
public record Quote(String symbol, double price, long volume, Instant timestamp) {
}
