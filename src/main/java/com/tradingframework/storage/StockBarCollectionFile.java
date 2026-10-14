package com.tradingframework.storage;

import com.tradingframework.model.StockBarCollection;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Saves a {@link StockBarCollection} to a local file and loads it back, using Java serialization.
 * Saves are atomic: data is written to a temp file next to the target, then moved into place.
 */
public final class StockBarCollectionFile {
    private static final System.Logger LOG = System.getLogger(StockBarCollectionFile.class.getName());
    private StockBarCollectionFile() {
    }

    public static void save(StockBarCollection collection, Path file) {
        Path abs = file.toAbsolutePath();
        Path tmp = null;
        try {
            Files.createDirectories(abs.getParent());
            tmp = Files.createTempFile(abs.getParent(), abs.getFileName().toString(), ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp);
                 ObjectOutputStream oos = new ObjectOutputStream(new java.io.BufferedOutputStream(out))) {
                oos.writeObject(collection);
            }
            try {
                Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
            throw new StorageException("Failed to save StockBarCollection to " + file, e);
        }
    }

    public static StockBarCollection load(Path file) {
        try (InputStream in = Files.newInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(new java.io.BufferedInputStream(in))) {
            Object obj = ois.readObject();
            if (obj instanceof StockBarCollection collection) {
                return collection;
            }

            LOG.log(System.Logger.Level.INFO, "File " + file + " does not contain a StockBarCollection");
        } catch (IOException | ClassNotFoundException e) {
            LOG.log(System.Logger.Level.INFO, "Failed to load StockBarCollection from " + file);
        }

        return new StockBarCollection();
    }
}
