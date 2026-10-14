package com.tradingframework.data;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * twelvedata.com time series with {@code format=CSV}.
 * CSV columns: {@code datetime,open,high,low,close,volume} (comma delimiter requested explicitly).
 * At most 5000 bars per request.
 */
public class TwelveDataSource implements PriceDataSource {

    private final String apiKey;

    public TwelveDataSource(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "twelvedata";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public URI buildUri(String symbol, LocalDate from, LocalDate to) {
        return URI.create("https://api.twelvedata.com/time_series?interval=1day&outputsize=5000&order=ASC"
                + "&symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                + "&start_date=" + from + "&end_date=" + to
                + "&format=CSV&delimiter=,"
                + "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
    }
}
