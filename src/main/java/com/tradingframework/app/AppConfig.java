package com.tradingframework.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Application settings from {@code config/application.properties} (falling back to the bundled
 * defaults). Any key can be overridden by an environment variable named like the key in upper case
 * with dots replaced by underscores, e.g. {@code finnhub.apiKey} -&gt; {@code FINNHUB_APIKEY}.
 */
public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig(Path file) {
        try (InputStream defaults = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (defaults != null) props.load(defaults);
            if (file != null && Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    props.load(r);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String get(String key, String defaultValue) {
        String env = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (env != null && !env.isBlank()) return env.trim();
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? defaultValue : v.trim();
    }

    public String get(String key) {
        return get(key, null);
    }

    public double getDouble(String key, double defaultValue) {
        String v = get(key);
        return v == null ? defaultValue : Double.parseDouble(v);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = get(key);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    public LocalDate getDate(String key, LocalDate defaultValue) {
        String v = get(key);
        return v == null ? defaultValue : LocalDate.parse(v);
    }

    public List<String> getList(String key) {
        String v = get(key);
        if (v == null) return List.of();
        return Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
