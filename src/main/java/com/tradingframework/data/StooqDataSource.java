package com.tradingframework.data;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * stooq.com daily history. CSV columns: {@code Date,Open,High,Low,Close,Volume}.
 * US tickers are suffixed with {@code .us} unless the symbol already has an exchange suffix.
 */
public class StooqDataSource implements PriceDataSource {

    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;

    private final String baseUrl;

    public StooqDataSource() {
        this("https://stooq.com/q/d/l/");
    }

    public StooqDataSource(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String name() {
        return "stooq";
    }

    @Override
    public URI buildUri(String symbol, LocalDate from, LocalDate to) {
        String s = symbol.toLowerCase(Locale.ROOT);
        if (!s.contains(".")) s += ".us";
        return URI.create(baseUrl + "?s=" + s + "&i=d&d1=" + BASIC.format(from) + "&d2=" + BASIC.format(to));
    }
}
