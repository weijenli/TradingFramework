package com.tradingframework.data;

import java.net.URI;
import java.time.LocalDate;

/**
 * A website that serves end-of-day prices as CSV over HTTP. Implementations only know how to
 * build the request URL; downloading and parsing are shared ({@link HttpCsvDownloader},
 * {@link CsvBarParser}) because the parser maps columns by header name.
 */
public interface PriceDataSource {

    String name();

    URI buildUri(String symbol, LocalDate from, LocalDate to);

    /** False when the source needs an API key that is not configured; such sources are skipped. */
    default boolean isConfigured() {
        return true;
    }
}
