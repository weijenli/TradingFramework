package com.tradingframework.realtime;

import java.util.Collection;
import java.util.function.Consumer;

/** A source of real-time quotes, e.g. an exchange or broker WebSocket. */
public interface PriceFeed extends AutoCloseable {

    void addListener(Consumer<Quote> listener);

    /** Subscribe to symbols; may be called before or after {@link #start()}. */
    void subscribe(Collection<String> symbols);

    void start();

    @Override
    void close();
}
