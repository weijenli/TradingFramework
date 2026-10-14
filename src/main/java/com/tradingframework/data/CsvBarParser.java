package com.tradingframework.data;

import com.tradingframework.model.StockBar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses daily OHLCV CSV files from different providers by mapping header names, so column
 * order and naming differences ("Date"/"timestamp", "Adj Close"/"adjClose") don't matter.
 * Rows with missing values are skipped; output is sorted by date with duplicates removed.
 */
public class CsvBarParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,           // 2024-01-31
            DateTimeFormatter.BASIC_ISO_DATE,           // 20240131
            DateTimeFormatter.ofPattern("M/d/yyyy"),    // 1/31/2024
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH)); // 31-Jan-2024

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        for (String a : List.of("date", "timestamp", "time", "day", "datetime")) ALIASES.put(a, "date");
        for (String a : List.of("open", "o")) ALIASES.put(a, "open");
        for (String a : List.of("high", "h")) ALIASES.put(a, "high");
        for (String a : List.of("low", "l")) ALIASES.put(a, "low");
        for (String a : List.of("close", "c", "last", "closelast")) ALIASES.put(a, "close");
        for (String a : List.of("volume", "vol", "v")) ALIASES.put(a, "volume");
        for (String a : List.of("adjclose", "adjustedclose")) ALIASES.put(a, "adjclose");
    }

    public List<StockBar> parse(String symbol, String csv) {
        return parse(symbol, new StringReader(csv));
    }

    public List<StockBar> parse(String symbol, Reader reader) {
        try (BufferedReader in = new BufferedReader(reader)) {
            String headerLine = in.readLine();
            if (headerLine == null) return List.of();
            Map<String, Integer> cols = mapHeader(splitLine(stripBom(headerLine)));
            for (String required : List.of("date", "open", "high", "low", "close")) {
                if (!cols.containsKey(required)) {
                    throw new IllegalArgumentException("CSV for " + symbol + " has no '" + required + "' column: " + headerLine);
                }
            }

            TreeMap<LocalDate, StockBar> byDate = new TreeMap<>();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                StockBar bar = parseRow(symbol, splitLine(line), cols);
                if (bar != null) byDate.put(bar.getDate(), bar);
            }
            return new ArrayList<>(byDate.values());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static StockBar parseRow(String symbol, List<String> f, Map<String, Integer> cols) {
        try {
            LocalDate date = parseDate(field(f, cols.get("date")));
            double open = parseDouble(field(f, cols.get("open")));
            double high = parseDouble(field(f, cols.get("high")));
            double low = parseDouble(field(f, cols.get("low")));
            double close = parseDouble(field(f, cols.get("close")));
            long volume = cols.containsKey("volume") ? (long) parseDouble(field(f, cols.get("volume"))) : 0L;
            double adj = cols.containsKey("adjclose") ? parseDouble(field(f, cols.get("adjclose"))) : close;
            if (date == null || Double.isNaN(open) || Double.isNaN(high) || Double.isNaN(low) || Double.isNaN(close)) {
                return null;
            }
            return new StockBar(symbol, date, open, high, low, close, volume, Double.isNaN(adj) ? close : adj);
        } catch (RuntimeException e) {
            return null; // malformed row: skip it
        }
    }

    private static Map<String, Integer> mapHeader(List<String> header) {
        Map<String, Integer> cols = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            String canonical = ALIASES.get(key);
            if (canonical != null) cols.putIfAbsent(canonical, i);
        }
        return cols;
    }

    private static String field(List<String> fields, int index) {
        return index < fields.size() ? fields.get(index).strip() : "";
    }

    static LocalDate parseDate(String s) {
        if (s.isEmpty()) return null;
        if (s.length() > 10 && (s.charAt(10) == 'T' || s.charAt(10) == ' ')) s = s.substring(0, 10); // drop time part
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    static double parseDouble(String s) {
        if (s.isEmpty() || s.equalsIgnoreCase("null") || s.equalsIgnoreCase("n/a") || s.equals("-")) return Double.NaN;
        return Double.parseDouble(s.replace(",", "").replace("$", ""));
    }

    /** Splits one CSV line, honouring double-quoted fields. */
    static List<String> splitLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
    }
}
