package com.tradingframework.daily;

import com.tradingframework.TestBars;
import com.tradingframework.data.CsvBarParser;
import com.tradingframework.data.HttpCsvDownloader;
import com.tradingframework.data.StockDataService;
import com.tradingframework.storage.CsvFileBarRepository;
import com.tradingframework.strategy.EntryStrategy;
import com.tradingframework.strategy.Exits;
import com.tradingframework.strategy.Parameters;
import com.tradingframework.strategy.PickStrategy;
import com.tradingframework.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyScannerTest {

    @TempDir
    Path tmp;

    @Test
    void reportsPicksWithTheStrategyThatProducedThem() throws Exception {
        StockDataService data = new StockDataService(List.of(), new HttpCsvDownloader(), new CsvBarParser(),
                List.of(new CsvFileBarRepository(tmp)), LocalDate.of(2020, 1, 1));
        data.save(TestBars.flat("UP", 1, 2, 3, 4, 5).bars());
        data.save(TestBars.flat("DOWN", 5, 4, 3, 2, 1).bars());

        TradingStrategy rising = new TradingStrategy("RISING", Parameters.of("n", 1), PickStrategy.any(),
                EntryStrategy.of("close up", (s, i) -> s.close(i) > s.close(i - 1)), Exits.stopLoss(0.1));
        TradingStrategy falling = new TradingStrategy("FALLING", Parameters.empty(), PickStrategy.any(),
                EntryStrategy.of("close down", (s, i) -> s.close(i) < s.close(i - 1)), Exits.stopLoss(0.1));
        TradingStrategy any = new TradingStrategy("ANY", Parameters.empty(), PickStrategy.any(),
                EntryStrategy.of("always", (s, i) -> true), Exits.stopLoss(0.1));

        LocalDate asOf = TestBars.START.plusDays(4);
        DailyScanResult result = new DailyScanner(data, List.of(rising, falling, any), 30, 5)
                .scan(List.of("UP", "DOWN", "MISSING"), false, asOf);

        assertEquals(List.of("ANY", "FALLING"), result.picksBySymbol().get("DOWN").stream().map(StockPick::strategyName).sorted().toList());
        assertEquals(List.of("ANY", "RISING"), result.picksBySymbol().get("UP").stream().map(StockPick::strategyName).sorted().toList());
        assertEquals("RISING(n=1)", result.picksBySymbol().get("UP").stream()
                .filter(p -> p.strategyName().equals("RISING")).findFirst().orElseThrow().strategyLabel());
        assertEquals(Set.of("MISSING"), result.errors().keySet());

        Path report = new PickReportWriter(tmp.resolve("reports")).write(result);
        assertEquals(5, Files.readAllLines(report).size()); // header + 4 picks
    }

    @Test
    void schedulerSkipsWeekendsAndHolidays() {
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDate thanksgiving = LocalDate.of(2026, 11, 26);
        MarketCloseScheduler scheduler = new MarketCloseScheduler(LocalTime.of(16, 30), ny, Set.of(thanksgiving));

        // Friday after the run time -> Monday
        ZonedDateTime fri = ZonedDateTime.of(2026, 10, 9, 17, 0, 0, 0, ny);
        assertEquals(ZonedDateTime.of(2026, 10, 12, 16, 30, 0, 0, ny), scheduler.nextRun(fri));
        // Same day before the run time
        ZonedDateTime morning = ZonedDateTime.of(2026, 10, 6, 9, 0, 0, 0, ny);
        assertEquals(ZonedDateTime.of(2026, 10, 6, 16, 30, 0, 0, ny), scheduler.nextRun(morning));
        // Wednesday evening before Thanksgiving -> Friday
        ZonedDateTime wed = ZonedDateTime.of(2026, 11, 25, 18, 0, 0, 0, ny);
        assertEquals(LocalDate.of(2026, 11, 27), scheduler.nextRun(wed).toLocalDate());
        assertTrue(!scheduler.isTradingDay(thanksgiving));
        scheduler.close();
    }
}
