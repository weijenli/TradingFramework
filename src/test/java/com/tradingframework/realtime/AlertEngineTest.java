package com.tradingframework.realtime;

import com.tradingframework.TestBars;
import com.tradingframework.model.BarSeries;
import com.tradingframework.strategy.EntryStrategy;
import com.tradingframework.strategy.Exits;
import com.tradingframework.strategy.Parameters;
import com.tradingframework.strategy.PickStrategy;
import com.tradingframework.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertEngineTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    /** Feed driven manually by the test. */
    static class ManualFeed implements PriceFeed {
        final List<Consumer<Quote>> listeners = new ArrayList<>();
        @Override public void addListener(Consumer<Quote> l) { listeners.add(l); }
        @Override public void subscribe(Collection<String> symbols) { }
        @Override public void start() { }
        @Override public void close() { }
        void push(String sym, double price, long vol, Instant t) { listeners.forEach(l -> l.accept(new Quote(sym, price, vol, t))); }
    }

    private final Instant t0 = TestBars.START.plusDays(10).atTime(14, 0).toInstant(ZoneOffset.UTC);

    private AlertEngine engine(ManualFeed feed, List<AlertStrategy> alerts, List<Signal> received) {
        LiveSeriesTracker tracker = new LiveSeriesTracker(UTC);
        tracker.setHistory(TestBars.flat("AAPL", 100, 100, 100, 100, 100)); // prev close 100
        AlertEngine e = new AlertEngine(feed, tracker, alerts, List.of(received::add), Duration.ofMinutes(10), Duration.ZERO);
        e.start(List.of("AAPL"));
        return e;
    }

    @Test
    void priceCrossAndPercentMoveAlertsFireWithCooldown() {
        ManualFeed feed = new ManualFeed();
        List<Signal> got = new ArrayList<>();
        engine(feed, List.of(Alerts.priceCrossesAbove("AAPL", 102), Alerts.percentMoveFromPreviousClose(0.03)), got);

        feed.push("AAPL", 101, 10, t0);
        assertTrue(got.isEmpty());
        feed.push("AAPL", 102.5, 10, t0.plusSeconds(1));
        assertEquals(1, got.size());
        assertEquals(Signal.SignalType.ALERT, got.get(0).type());
        feed.push("AAPL", 103.5, 10, t0.plusSeconds(2));   // +3.5% -> move alert
        assertEquals(2, got.size());
        feed.push("AAPL", 101, 10, t0.plusSeconds(3));
        feed.push("AAPL", 104, 10, t0.plusSeconds(4));     // both again, but within cooldown
        assertEquals(2, got.size());
        feed.push("AAPL", 101, 10, t0.plusSeconds(700));
        feed.push("AAPL", 104, 10, t0.plusSeconds(701));   // cooldown elapsed
        assertEquals(4, got.size());
    }

    @Test
    void ignoresUnwatchedSymbols() {
        ManualFeed feed = new ManualFeed();
        List<Signal> got = new ArrayList<>();
        engine(feed, List.of(Alerts.percentMoveFromPreviousClose(0.0)), got);
        feed.push("MSFT", 500, 10, t0);
        assertTrue(got.isEmpty());
    }

    @Test
    void dailyStrategyEntryIsEvaluatedOnLiveBar() {
        ManualFeed feed = new ManualFeed();
        List<Signal> got = new ArrayList<>();
        TradingStrategy jump = new TradingStrategy("JUMP", Parameters.empty(), PickStrategy.any(),
                EntryStrategy.of("close > prev close * 1.05", (s, i) -> i >= 1 && s.close(i) > s.close(i - 1) * 1.05),
                Exits.stopLoss(0.1));
        engine(feed, List.of(Alerts.entrySignal(jump)), got);

        feed.push("AAPL", 104, 10, t0);
        assertTrue(got.isEmpty());
        feed.push("AAPL", 106, 10, t0.plusSeconds(5));
        assertEquals(1, got.size());
        assertEquals(Signal.SignalType.BUY, got.get(0).type());
        assertEquals("AAPL", got.get(0).symbol());
    }

    @Test
    void trackerBuildsLiveBarAndRollsOverDays() {
        LiveSeriesTracker tracker = new LiveSeriesTracker(UTC);
        tracker.setHistory(TestBars.flat("AAPL", 100, 100));
        LocalDate day = LocalDate.of(2024, 3, 1);
        Instant t = day.atTime(15, 0).toInstant(ZoneOffset.UTC);

        tracker.onQuote(new Quote("AAPL", 101, 100, t));
        tracker.onQuote(new Quote("AAPL", 99, 50, t.plusSeconds(60)));
        BarSeries s = tracker.onQuote(new Quote("AAPL", 100.5, 25, t.plusSeconds(120)));
        assertEquals(3, s.size());
        assertEquals(101, s.last().getOpen());
        assertEquals(101, s.last().getHigh());
        assertEquals(99, s.last().getLow());
        assertEquals(100.5, s.last().getClose());
        assertEquals(175, s.last().getVolume());

        BarSeries next = tracker.onQuote(new Quote("AAPL", 102, 10, t.plus(Duration.ofDays(1))));
        assertEquals(4, next.size());
        assertEquals(100.5, next.close(2));
        assertEquals(day.plusDays(1), next.last().getDate());
    }
}
