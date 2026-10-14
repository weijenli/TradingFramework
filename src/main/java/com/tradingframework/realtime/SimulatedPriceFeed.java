package com.tradingframework.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Random-walk quote generator for demos and testing alert strategies without a live feed. */
public class SimulatedPriceFeed implements PriceFeed {

    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private final Map<String, Double> startPrices;
    private final List<Consumer<Quote>> listeners = new CopyOnWriteArrayList<>();
    private final Duration interval;
    private final double volatility;
    private final Random random;
    private ScheduledExecutorService executor;

    /**
     * @param startPrices starting price per symbol (e.g. yesterday's close); unknown symbols start at 100
     * @param volatility  standard deviation of each tick's return, e.g. 0.002
     */
    public SimulatedPriceFeed(Map<String, Double> startPrices, Duration interval, double volatility, long seed) {
        this.startPrices = Map.copyOf(startPrices);
        this.interval = interval;
        this.volatility = volatility;
        this.random = new Random(seed);
    }

    @Override
    public void addListener(Consumer<Quote> listener) {
        listeners.add(listener);
    }

    @Override
    public void subscribe(Collection<String> symbols) {
        for (String s : symbols) {
            String sym = s.toUpperCase(Locale.ROOT);
            prices.putIfAbsent(sym, startPrices.getOrDefault(sym, 100.0));
        }
    }

    @Override
    public synchronized void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "simulated-feed");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::tick, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Emits one quote per subscribed symbol. Public so tests can drive it synchronously. */
    public void tick() {
        Instant now = Instant.now();
        for (Map.Entry<String, Double> e : prices.entrySet()) {
            double p = Math.max(0.01, e.getValue() * (1 + random.nextGaussian() * volatility));
            p = Math.round(p * 100) / 100.0;
            e.setValue(p);
            Quote q = new Quote(e.getKey(), p, 100L * (1 + random.nextInt(50)), now);
            for (Consumer<Quote> l : listeners) l.accept(q);
        }
    }

    @Override
    public synchronized void close() {
        if (executor != null) executor.shutdownNow();
    }
}
