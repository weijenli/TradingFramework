package com.tradingframework.storage;

import com.tradingframework.model.StockBar;
import com.tradingframework.model.StockBarCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockBarCollectionFileTest {

    @Test
    void roundTrip(@TempDir Path dir) {
        StockBarCollection original = new StockBarCollection();
        original.addOne(new StockBar("AAPL", LocalDate.of(2024, 1, 2), 1, 2, 0.5, 1.5, 100, 1.4));
        original.addOne(new StockBar("AAPL", LocalDate.of(2024, 1, 3), 2, 3, 1.5, 2.5, 200));
        original.addOne(new StockBar("MSFT", LocalDate.of(2024, 1, 2), 10, 11, 9, 10.5, 300));

        Path file = dir.resolve("sub/bars.ser");
        StockBarCollectionFile.save(original, file);
        StockBarCollection loaded = StockBarCollectionFile.load(file);

        assertEquals(original.getStockBarMap().keySet(), loaded.getStockBarMap().keySet());
        List<StockBar> aapl = loaded.getStock("AAPL");
        assertEquals(2, aapl.size());
        assertEquals(1.4, aapl.get(0).getAdjClose());
        assertEquals(300, loaded.getStock("MSFT").get(0).getVolume());
    }

    @Test
    void loadRejectsNonCollection(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.ser");
        Files.writeString(file, "not a serialized object");
        assertThrows(StorageException.class, () -> StockBarCollectionFile.load(file));
    }
}
