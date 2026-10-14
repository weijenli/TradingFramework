package com.tradingframework.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;

/** Destination for signals: console, log file, webhook (Slack/Teams/Discord-style), ... */
@FunctionalInterface
public interface SignalSink {

    void send(Signal signal);

    static SignalSink console(PrintStream out) {
        return s -> out.println(">>> SIGNAL " + s.format());
    }

    /** Appends one CSV line per signal. */
    static SignalSink csvFile(Path file) {
        return s -> {
            try {
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                boolean isNew = !Files.exists(file);
                String line = (isNew ? "time,symbol,type,strategy,price,message\n" : "")
                        + String.join(",", s.time().toString(), s.symbol(), s.type().name(),
                        '"' + s.strategy().replace("\"", "'") + '"', Double.toString(s.price()),
                        '"' + s.message().replace("\"", "'") + '"') + "\n";
                Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /** POSTs {@code {"text": "..."}} as JSON (accepted by Slack incoming webhooks and similar). Fire-and-forget. */
    static SignalSink webhook(URI url) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ObjectMapper json = new ObjectMapper();
        System.Logger log = System.getLogger(SignalSink.class.getName());
        return s -> {
            try {
                String body = json.writeValueAsString(Map.of("text", s.format()));
                HttpRequest req = HttpRequest.newBuilder(url)
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                client.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                        .thenAccept(r -> {
                            if (r.statusCode() >= 300) log.log(System.Logger.Level.WARNING, "Webhook returned HTTP {0}", r.statusCode());
                        })
                        .exceptionally(e -> {
                            log.log(System.Logger.Level.WARNING, "Webhook failed: {0}", e.getMessage());
                            return null;
                        });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
