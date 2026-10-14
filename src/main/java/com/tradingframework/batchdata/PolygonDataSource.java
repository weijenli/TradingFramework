package com.tradingframework.batchdata;

import com.tradingframework.app.AppConfig;
import com.tradingframework.model.StockBar;
import com.tradingframework.model.StockBarCollection;
import org.h2.util.json.*;
import org.h2.util.json.JSONObject;
import org.h2.util.json.JSONValue;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class PolygonDataSource extends BatchPriceDataSource{
    private static final System.Logger LOG = System.getLogger(PolygonDataSource.class.getName());

    public final String polygonUrl;
    public final String polygonKey;
    public final String username;
    public final String password;
    private final String FILE_EXTENSION = "json";

    public PolygonDataSource(AppConfig config) {
        super(config, "polygon");
        this.polygonUrl = config.get("polygon.url", "url");
        this.polygonKey = "?adjusted=true&apiKey=" + config.get("polygon.key", "key");
        this.username = config.get("polygon.username", "username");
        this.password = config.get("polygon.password", "password");
    }

    public void downloadOneDay(String date) {
        LOG.log(System.Logger.Level.INFO, "Download " + date);
        try {
            String data = downloadEod(polygonUrl + date + polygonKey, username, password);
            if (data == null || data.length() < 100) {
                throw new RuntimeException("Blank data from Polygon");
            }
            parseEodAndSave(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    JSONValue[] getJsonArray(String data) {
        JSONValue root = JSONStringSource.parse(data, new JSONValueTarget());
        if (!(root instanceof JSONObject obj)) {
            throw new IllegalArgumentException("Top-level JSON is not an object");
        }
        if (!(obj.getFirst("results") instanceof JSONArray arr)) {
            throw new IllegalArgumentException("Missing \"results\" array");
        }

        return arr.getArray();
    }

    @Override
    String[] getOneLine(JSONObject o, String defaultDate) {
        String yyyymmdd = defaultDate == null ? getDateStr(num(o, "t").longValue()) : defaultDate;

        String symbol = str(o, "T");
        if (symbol.contains("-") || symbol.contains(".")) return null;

        double open = num(o, "o").doubleValue();
        double high = num(o, "h").doubleValue();
        double low = num(o, "l").doubleValue();
        double close = num(o, "c").doubleValue();
        double volume = num(o, "v").doubleValue();

        return new String[] {symbol + "," + yyyymmdd + "," + open + "," + high + "," + low + "," + close + "," + volume + "\n", yyyymmdd};
    }

    @Override
    String getFilename(String date) {
        return date;
    }

    @Override
    Path getModelFile() {
        return dir.getParent().resolve(sourceName + ".mdl");
    }

    private String getDateStr(long millisecond) {
        LocalDate date = Instant.ofEpochMilli(millisecond)
                .atZone(ZoneId.systemDefault()) // Or ZoneOffset.UTC for UTC date
                .toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return date.format(formatter);
    }

}
