package com.tradingframework.data;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * alphavantage.co TIME_SERIES_DAILY with {@code datatype=csv}.
 * CSV columns: {@code timestamp,open,high,low,close,volume}.
 * <p>
 * The API has no date-range parameter: "compact" returns ~100 trading days, "full" the whole
 * history (may require a premium key). Rows outside the requested range are filtered by
 * {@link StockDataService}.
 */
public class AlphaVantageDataSource implements PriceDataSource {

    private final String apiKey;

    public AlphaVantageDataSource(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "alphavantage";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public URI buildUri(String symbol, LocalDate from, LocalDate to) {
        String outputSize = ChronoUnit.DAYS.between(from, LocalDate.now()) <= 140 ? "compact" : "full";
        return URI.create("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY"
                + "&symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                + "&outputsize=" + outputSize
                + "&datatype=csv"
                + "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
    }
}
