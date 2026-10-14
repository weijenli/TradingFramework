package com.tradingframework.backtest;

import com.tradingframework.TestBars;
import com.tradingframework.data.SyntheticDataGenerator;
import com.tradingframework.model.BarSeries;
import com.tradingframework.model.StockBar;
import com.tradingframework.strategy.EntryStrategy;
import com.tradingframework.strategy.ExitSignal;
import com.tradingframework.strategy.ExitStrategy;
import com.tradingframework.strategy.Exits;
import com.tradingframework.strategy.Parameters;
import com.tradingframework.strategy.PickStrategy;
import com.tradingframework.strategy.StrategyCatalog;
import com.tradingframework.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.tradingframework.TestBars.bar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestEngineTest {

    private static final BacktestConfig NO_COSTS = new BacktestConfig(10_000, 1.0, 0, 0, null, null);

    /** Enter on bar 0's signal, fill at bar 1's open, exit next open when rule says so. */
    @Test
    void entersAtNextOpenAndExitsAtNextOpen() {
        BarSeries s = new BarSeries("X", List.of(
                bar("X", 0, 10, 10, 10, 10),
                bar("X", 1, 20, 22, 19, 21),   // entry fill at open 20
                bar("X", 2, 22, 26, 22, 25),   // exit signal at close
                bar("X", 3, 30, 31, 29, 30))); // exit fill at open 30
        TradingStrategy strategy = new TradingStrategy("T", Parameters.empty(), PickStrategy.any(),
                EntryStrategy.of("first bar", (ser, i) -> i == 0),
                ExitStrategy.of("on bar 2", (ser, i, p) -> i == 2 ? Optional.of(ExitSignal.atNextOpen("RULE")) : Optional.empty()));

        BacktestResult r = new BacktestEngine().run(strategy, List.of(s), NO_COSTS);

        assertEquals(1, r.totalTrades());
        Trade t = r.trades().get(0);
        assertEquals(20.0, t.entryPrice());
        assertEquals(30.0, t.exitPrice());
        assertEquals(500, t.shares());             // 10,000 / 20
        assertEquals(5_000.0, t.profit(), 1e-9);
        assertEquals("RULE", t.exitReason());
        assertEquals(1.0, r.winRate());
    }

    @Test
    void stopLossFillsIntradayAtStopPrice() {
        BarSeries s = new BarSeries("X", List.of(
                bar("X", 0, 100, 100, 100, 100),
                bar("X", 1, 100, 101, 99, 100),
                bar("X", 2, 99, 99, 85, 88)));   // low pierces 90 stop
        TradingStrategy strategy = new TradingStrategy("T", Parameters.empty(), PickStrategy.any(),
                EntryStrategy.of("first bar", (ser, i) -> i == 0), Exits.stopLoss(0.10));

        Trade t = new BacktestEngine().run(strategy, List.of(s), NO_COSTS).trades().get(0);
        assertEquals(90.0, t.exitPrice(), 1e-9);
        assertEquals("STOP_LOSS", t.exitReason());
        assertFalse(t.isWin());
    }

    @Test
    void stopLossFillsAtOpenOnGapDown() {
        BarSeries s = new BarSeries("X", List.of(
                bar("X", 0, 100, 100, 100, 100),
                bar("X", 1, 100, 101, 99, 100),
                bar("X", 2, 80, 82, 78, 81)));
        TradingStrategy strategy = new TradingStrategy("T", Parameters.empty(), PickStrategy.any(),
                EntryStrategy.of("first bar", (ser, i) -> i == 0), Exits.stopLoss(0.10));
        assertEquals(80.0, new BacktestEngine().run(strategy, List.of(s), NO_COSTS).trades().get(0).exitPrice(), 1e-9);
    }

    @Test
    void openPositionIsClosedAtEndOfTestAndCostsAreApplied() {
        BarSeries s = TestBars.flat("X", 10, 10, 10, 10);
        TradingStrategy strategy = new TradingStrategy("T", Parameters.empty(), PickStrategy.any(),
                EntryStrategy.of("first bar", (ser, i) -> i == 0),
                ExitStrategy.of("never", (ser, i, p) -> Optional.empty()));
        BacktestConfig cfg = new BacktestConfig(10_000, 1.0, 5, 0, null, null);

        Trade t = new BacktestEngine().run(strategy, List.of(s), cfg).trades().get(0);
        assertEquals("END_OF_TEST", t.exitReason());
        assertEquals(-10.0, t.profit(), 1e-9); // two $5 commissions
    }

    @Test
    void entriesRespectFromDate() {
        BarSeries s = TestBars.flat("X", 1, 2, 3, 4, 5, 6);
        TradingStrategy strategy = new TradingStrategy("T", Parameters.empty(), PickStrategy.any(),
                EntryStrategy.of("always", (ser, i) -> true), Exits.maxHoldingBars(0));
        LocalDate from = TestBars.START.plusDays(3);
        BacktestResult r = new BacktestEngine().run(strategy, List.of(s), NO_COSTS.withDateRange(from, null));
        assertTrue(r.trades().stream().allMatch(t -> t.entryDate().isAfter(from)));
    }

    @Test
    void metricsAreConsistent() {
        List<BarSeries> universe = List.of(
                new BarSeries("A", SyntheticDataGenerator.generate("A", LocalDate.of(2018, 1, 1), 1200, 50, 1)),
                new BarSeries("B", SyntheticDataGenerator.generate("B", LocalDate.of(2018, 1, 1), 1200, 80, 2)));
        for (TradingStrategy strategy : StrategyCatalog.defaults()) {
            BacktestResult r = new BacktestEngine().run(strategy, universe, BacktestConfig.defaults());
            assertEquals(r.totalTrades(), r.winningTrades() + r.losingTrades());
            assertEquals(r.trades().stream().mapToDouble(Trade::profit).sum(), r.totalProfit(), 1e-6);
            assertTrue(r.winRate() >= 0 && r.winRate() <= 1);
            assertTrue(r.maxDrawdownPct() >= 0 && r.maxDrawdownPct() <= 1);
            for (Trade t : r.trades()) {
                assertFalse(t.exitDate().isBefore(t.entryDate()));
                assertTrue(t.shares() > 0);
            }
        }
    }

    @Test
    void noLookAheadEntryFillsUseNextBarOpen() {
        List<StockBar> bars = SyntheticDataGenerator.generate("A", LocalDate.of(2018, 1, 1), 800, 50, 3);
        BarSeries s = new BarSeries("A", bars);
        BacktestResult r = new BacktestEngine().run(StrategyCatalog.maCrossover(Parameters.of("fast", 5, "slow", 20)),
                List.of(s), NO_COSTS);
        assertTrue(r.totalTrades() > 0);
        for (Trade t : r.trades()) {
            int i = s.indexOnOrBefore(t.entryDate());
            assertEquals(s.open(i), t.entryPrice(), 1e-9);
        }
    }

    @Test
    void sweepSkipsInvalidCombinationsAndSortsBestFirst() {
        List<BarSeries> universe = List.of(
                new BarSeries("A", SyntheticDataGenerator.generate("A", LocalDate.of(2018, 1, 1), 1000, 50, 4)));
        ParameterGrid grid = new ParameterGrid().values("fast", 5, 20, 60).values("slow", 30, 50);
        assertEquals(6, grid.size());

        List<BacktestResult> results = new ParameterSweep().run(StrategyCatalog::maCrossover, grid, universe, BacktestConfig.defaults());

        assertEquals(4, results.size()); // fast=60 rejected for both slow values
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).totalProfit() >= results.get(i).totalProfit());
        }
    }

    @Test
    void parameterGridRange() {
        List<Parameters> combos = new ParameterGrid().range("x", 0.1, 0.3, 0.1).values("y", 1, 2).combinations();
        assertEquals(6, combos.size());
        assertEquals(Parameters.of("x", 0.3, "y", 2), combos.get(5));
    }
}
