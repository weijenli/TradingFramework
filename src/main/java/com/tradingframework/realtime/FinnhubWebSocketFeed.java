package com.tradingframework.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Real-time US stock trades from Finnhub's WebSocket API ({@code wss://ws.finnhub.io}).
 * Messages look like {@code {"type":"trade","data":[{"s":"AAPL","p":187.2,"v":100,"t":1700000000000}]}}.
 * Reconnects with exponential backoff and re-subscribes after a disconnect.
 * Any other exchange/broker feed can be plugged in by implementing {@link PriceFeed}.
 */
public class FinnhubWebSocketFeed implements PriceFeed {

    private static final System.Logger LOG = System.getLogger(FinnhubWebSocketFeed.class.getName());

    private final URI uri;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final Set<String> symbols = ConcurrentHashMap.newKeySet();
    private final List<Consumer<Quote>> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService reconnector = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "finnhub-reconnect");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocket socket;
    private volatile boolean closed;
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);
    private int failures;

    public FinnhubWebSocketFeed(String apiToken) {
        if (apiToken == null || apiToken.isBlank()) throw new IllegalArgumentException("Finnhub API token is required");
        this.uri = URI.create("wss://ws.finnhub.io?token=" + URLEncoder.encode(apiToken, StandardCharsets.UTF_8));
    }

    @Override
    public void addListener(Consumer<Quote> listener) {
        listeners.add(listener);
    }

    @Override
    public void subscribe(Collection<String> newSymbols) {
        for (String s : newSymbols) {
            String sym = s.toUpperCase(Locale.ROOT);
            if (symbols.add(sym) && socket != null) send(sym);
        }
    }

    @Override
    public void start() {
        connect();
    }

    private void connect() {
        if (closed) return;
        client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(uri, new Listener())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        LOG.log(System.Logger.Level.WARNING, "Finnhub connect failed: {0}", err.getMessage());
                        scheduleReconnect();
                    } else {
                        synchronized (this) { failures = 0; }
                        socket = ws;
                        LOG.log(System.Logger.Level.INFO, "Connected to Finnhub, subscribing {0}", symbols);
                        symbols.forEach(this::send);
                    }
                });
    }

    private synchronized void send(String symbol) {
        WebSocket ws = socket;
        if (ws == null) return;
        String msg = "{\"type\":\"subscribe\",\"symbol\":\"" + symbol + "\"}";
        // WebSocket allows only one outstanding send at a time, so chain them.
        sendChain = sendChain.handle((r, e) -> null).thenCompose(x -> ws.sendText(msg, true));
    }

    private synchronized void scheduleReconnect() {
        if (closed) return;
        socket = null;
        long delay = Math.min(60, 1L << Math.min(failures++, 6));
        LOG.log(System.Logger.Level.INFO, "Reconnecting to Finnhub in {0}s", delay);
        reconnector.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    void handleMessage(String message) {
        try {
            JsonNode root = json.readTree(message);
            if (!"trade".equals(root.path("type").asText())) return; // e.g. "ping"
            for (JsonNode d : root.path("data")) {
                Quote q = new Quote(d.path("s").asText(), d.path("p").asDouble(), d.path("v").asLong(),
                        Instant.ofEpochMilli(d.path("t").asLong()));
                for (Consumer<Quote> l : listeners) l.accept(q);
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Bad Finnhub message: " + message, e);
        }
    }

    @Override
    public void close() {
        closed = true;
        reconnector.shutdownNow();
        WebSocket ws = socket;
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String msg = buffer.toString();
                buffer.setLength(0);
                handleMessage(msg);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            LOG.log(System.Logger.Level.WARNING, "Finnhub closed: {0} {1}", statusCode, reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            LOG.log(System.Logger.Level.WARNING, "Finnhub error: {0}", error.toString());
            scheduleReconnect();
        }
    }
}
