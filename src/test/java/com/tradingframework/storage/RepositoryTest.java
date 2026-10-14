package com.tradingframework.storage;

import com.tradingframework.model.StockBar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryTest {

    private static final LocalDate D1 = LocalDate.of(2024, 1, 2);
    private static final LocalDate D2 = LocalDate.of(2024, 1, 3);
    private static final LocalDate D3 = LocalDate.of(2024, 1, 4);

    @TempDir
    Path tmp;

    @Test
    void csvFileRepositoryRoundTripsAndUpserts() {
        exercise(new CsvFileBarRepository(tmp.resolve("eod")));
    }

    @Test
    void jdbcRepositoryRoundTripsAndUpserts() {
        exercise(new JdbcBarRepository("jdbc:h2:mem:test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"));
    }

    @Test
    void stockBarIsJavaSerializable() throws Exception {
        StockBar bar = new StockBar("AAPL", D1, 1, 2, 0.5, 1.5, 100, 1.4);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(bar);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            StockBar copy = (StockBar) in.readObject();
            assertEquals(bar, copy);
            assertEquals(1.4, copy.getAdjClose());
        }
    }

    private static void exercise(BarRepository repo) {
        repo.saveAll(List.of(
                new StockBar("AAPL", D1, 10, 11, 9, 10.5, 1000),
                new StockBar("AAPL", D2, 10.5, 12, 10, 11.5, 2000),
                new StockBar("MSFT", D1, 300, 305, 299, 304, 500)));
        // upsert: D2 replaced, D3 added
        repo.saveAll(List.of(
                new StockBar("AAPL", D2, 10.5, 12.5, 10, 12, 2500),
                new StockBar("AAPL", D3, 12, 13, 11.5, 12.8, 1800)));

        List<StockBar> aapl = repo.findAll("AAPL");
        assertEquals(3, aapl.size());
        assertEquals(12.0, aapl.get(1).getClose());
        assertEquals(2500, aapl.get(1).getVolume());
        assertEquals(D3, aapl.get(2).getDate());
        assertEquals(List.of(D2), repo.find("AAPL", D2, D2).stream().map(StockBar::getDate).toList());
        assertEquals(Optional.of(D3), repo.lastDate("AAPL"));
        assertEquals(Optional.empty(), repo.lastDate("NONE"));
        assertEquals(Set.of("AAPL", "MSFT"), repo.symbols());
    }
}
