package com.tradingframework.data;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

/**
 * eodhd.com end-of-day API with {@code fmt=csv}.
 * CSV columns: {@code Date,Open,High,Low,Close,Adjusted_close,Volume}.
 * US tickers get the {@code .US} exchange suffix. The key {@code demo} works for a few tickers (e.g. AAPL).
 */
public class EodhdDataSource implements PriceDataSource {

    private final String apiToken;

    public EodhdDataSource(String apiToken) {
        this.apiToken = apiToken;
    }

    @Override
    public String name() {
        return "eodhd";
    }

    @Override
    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank();
    }

    @Override
    public URI buildUri(String symbol, LocalDate from, LocalDate to) {
        String s = symbol.toUpperCase(Locale.ROOT);
        if (!s.contains(".")) s += ".US";
        return URI.create("https://eodhd.com/api/eod/" + URLEncoder.encode(s, StandardCharsets.UTF_8)
                + "?fmt=csv&period=d&from=" + from + "&to=" + to
                + "&api_token=" + URLEncoder.encode(apiToken, StandardCharsets.UTF_8));
    }
}
