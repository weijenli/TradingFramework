package com.tradingframework.data;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * Downloads CSV documents over HTTP(S) with retries, rejecting responses that are not CSV price
 * data (HTML error pages, "No data", JSON rate-limit notes...). Optionally archives the raw CSV.
 */
public class HttpCsvDownloader {

    private static final System.Logger LOG = System.getLogger(HttpCsvDownloader.class.getName());

    private final HttpClient client;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Path rawArchiveDir;

    public HttpCsvDownloader() {
        this(null);
    }

    /** @param rawArchiveDir directory to save raw CSV responses in, or null to not archive */
    public HttpCsvDownloader(Path rawArchiveDir) {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                3, Duration.ofSeconds(2), rawArchiveDir);
    }

    public HttpCsvDownloader(HttpClient client, int maxAttempts, Duration retryDelay, Path rawArchiveDir) {
        this.client = client;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelay = retryDelay;
        this.rawArchiveDir = rawArchiveDir;
    }

    public String download(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "Mozilla/5.0 (TradingFramework EOD downloader)")
                .header("Accept", "text/csv,text/plain,*/*")
                .GET()
                .build();

        DataDownloadException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    String body = response.body();
                    validateCsv(uri, body);
                    return body;
                }
                last = new DataDownloadException("HTTP " + status + " from " + redact(uri));
                if (status >= 400 && status < 500 && status != 429) break; // not retryable
            } catch (DataDownloadException e) {
                throw e; // body was not CSV: retrying will not help
            } catch (IOException e) {
                last = new DataDownloadException("I/O error downloading " + redact(uri) + ": " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DataDownloadException("Interrupted downloading " + redact(uri), e);
            }
            LOG.log(System.Logger.Level.WARNING, "Attempt {0}/{1} failed: {2}", attempt, maxAttempts, last.getMessage());
            sleep(retryDelay.multipliedBy(attempt));
        }
        throw last;
    }

    /** Downloads and, if an archive directory is configured, saves the body as {@code <dir>/<source>/<symbol>.csv}. */
    public String download(URI uri, String sourceName, String symbol) {
        String body = download(uri);
        writeToFile(sourceName, symbol, body);
        return body;
    }

    private void writeToFile(String sourceName, String filename, String body) {
        if (rawArchiveDir != null) {
            try {
                Path dir = rawArchiveDir.resolve(sourceName);
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(filename.toUpperCase(Locale.ROOT) + ".csv"), body);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not archive for " + filename, e);
            }
        }
    }

    static void validateCsv(URI uri, String body) {
        String trimmed = body == null ? "" : body.strip();
        int eol = trimmed.indexOf('\n');
        String header = (eol < 0 ? trimmed : trimmed.substring(0, eol)).toLowerCase(Locale.ROOT);
        if (!(header.contains("open") && header.contains("close"))) {
            String snippet = trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
            throw new DataDownloadException("Response from " + redact(uri) + " is not price CSV: " + snippet);
        }
    }

    /** Hides API keys/tokens when a URL is logged. */
    static String redact(URI uri) {
        return uri.toString().replaceAll("(?i)(apikey|token)=[^&]*", "$1=***");
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
