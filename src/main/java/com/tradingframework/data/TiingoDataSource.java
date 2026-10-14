package com.tradingframework.data;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

/**
 * api.tiingo.com daily prices with {@code format=csv}.
 * CSV columns: {@code date,close,high,low,open,volume,adjClose,...}.
 */
public class TiingoDataSource implements PriceDataSource {

    private final String token;

    public TiingoDataSource(String token) {
        this.token = token;
    }

    @Override
    public String name() {
        return "tiingo";
    }

    @Override
    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }

    @Override
    public URI buildUri(String symbol, LocalDate from, LocalDate to) {
        return URI.create("https://api.tiingo.com/tiingo/daily/"
                + URLEncoder.encode(symbol.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8) + "/prices"
                + "?startDate=" + from + "&endDate=" + to
                + "&format=csv&resampleFreq=daily"
                + "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }
}
