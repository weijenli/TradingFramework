package com.tradingframework.realtime;

import com.tradingframework.model.BarSeries;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Subscribes to a {@link PriceFeed} for a few watched symbols, evaluates every
 * {@link AlertStrategy} on each quote and sends resulting {@link Signal}s to the sinks.
 * <p>
 * Evaluation per symbol is throttled to {@code evaluationInterval}, and the same
 * (symbol, strategy) pair is not re-sent within {@code cooldown}.
 */
public class AlertEngine implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(AlertEngine.class.getName());

    private final PriceFeed feed;
    private final LiveSeriesTracker tracker;
    private final List<AlertStrategy> alerts;
    private final List<SignalSink> sinks;
    private final Duration cooldown;
    private final Duration evaluationInterval;

    private final Map<String, Instant> lastSent = new HashMap<>();
    private final Map<String, Instant> lastEvaluated = new HashMap<>();
    private final Map<String, Double> lastPrice = new HashMap<>();
    private final List<Signal> sent = Collections.synchronizedList(new ArrayList<>());
    private Set<String> watched = Set.of();

    public AlertEngine(PriceFeed feed, LiveSeriesTracker tracker, List<AlertStrategy> alerts,
                       List<SignalSink> sinks, Duration cooldown, Duration evaluationInterval) {
        this.feed = feed;
        this.tracker = tracker;
        this.alerts = List.copyOf(alerts);
        this.sinks = List.copyOf(sinks);
        this.cooldown = cooldown;
        this.evaluationInterval = evaluationInterval;
    }

    public void start(Collection<String> symbols) {
        watched = symbols.stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        feed.addListener(this::onQuote);
        feed.subscribe(watched);
        feed.start();
        LOG.log(System.Logger.Level.INFO, "Alert engine watching {0} with {1} alert strategies", watched, alerts.size());
    }

    public synchronized void onQuote(Quote quote) {
        String sym = quote.symbol().toUpperCase(Locale.ROOT);
        if (!watched.isEmpty() && !watched.contains(sym)) return;

        BarSeries series = tracker.onQuote(quote);
        Instant prevEval = lastEvaluated.get(sym);
        if (prevEval != null && quote.timestamp().isBefore(prevEval.plus(evaluationInterval))) return;
        lastEvaluated.put(sym, quote.timestamp());

        AlertContext ctx = new AlertContext(quote, lastPrice.getOrDefault(sym, Double.NaN), series);
        lastPrice.put(sym, quote.price());

        for (AlertStrategy alert : alerts) {
            if (!alert.appliesTo(sym)) continue;
            Optional<Signal> signal;
            try {
                signal = alert.evaluate(ctx);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Alert " + alert.name() + " failed on " + sym, e);
                continue;
            }
            signal.ifPresent(this::dispatch);
        }
    }

    private void dispatch(Signal signal) {
        String key = signal.symbol() + "|" + signal.strategy();
        Instant prev = lastSent.get(key);
        if (prev != null && signal.time().isBefore(prev.plus(cooldown))) return;
        lastSent.put(key, signal.time());
        sent.add(signal);
        for (SignalSink sink : sinks) {
            try {
                sink.send(signal);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Signal sink failed", e);
            }
        }
    }

    /** Signals sent so far (after cooldown filtering). */
    public List<Signal> sentSignals() {
        synchronized (sent) {
            return List.copyOf(sent);
        }
    }

    @Override
    public void close() {
        feed.close();
    }
}
