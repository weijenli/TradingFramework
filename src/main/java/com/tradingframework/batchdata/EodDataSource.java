package com.tradingframework.batchdata;

import com.tradingframework.app.AppConfig;
import com.tradingframework.model.StockBarCollection;
import com.tradingframework.storage.StockBarCollectionFile;
import org.h2.util.json.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class EodDataSource extends BatchPriceDataSource{

    public static final String NASDAQ_PATH = "nasdaq/";
    public static final String NYSE_PATH = "nyse/";
    private final String FILE_EXTENSION = "json";
    public final String eoddataIrl;
    public final String eoddataKey;
    public final String username;
    public final String password;
    public final String exchange;

    public EodDataSource(AppConfig config, String exchange) {
        super(config, "eoddata");
        this.eoddataIrl = config.get("eoddata.url", "url");
        this.eoddataKey = config.get("eoddata.key", "key");
        this.username = config.get("eoddata.username", "username");
        this.password = config.get("eoddata.password", "password");
        this.exchange = exchange;
    }

    @Override
    JSONValue[] getJsonArray(String data) {
        JSONValue root = JSONStringSource.parse(data, new JSONValueTarget());
        if (!(root instanceof JSONArray arr)) {
            throw new IllegalArgumentException("Top-level JSON is not an array");
        }

        return arr.getArray();
    }

    @Override
    String[] getOneLine(JSONObject o, String defaultDate) {
        String yyyymmdd = defaultDate == null ? str(o, "dateStamp").replaceAll("-", "") : defaultDate;

        String symbol = str(o, "symbolCode");
        if (symbol.contains("-") || symbol.contains(".")) return null;

        double open = num(o, "open").doubleValue();
        double high = num(o, "high").doubleValue();
        double low = num(o, "low").doubleValue();
        double close = num(o, "close").doubleValue();
        double volume = num(o, "volume").doubleValue();

        return new String[] {symbol + "," + yyyymmdd + "," + open + "," + high + "," + low + "," + close + "," + volume + "\n", yyyymmdd};
    }

    @Override
    String getFilename(String date) {
        return exchange + "_" + date;
    }

    @Override
    protected boolean skipFile(String filename) {
        return filename.contains(exchange) || super.skipFile(filename);
    }

    @Override
    Path getModelFile() {
        return dir.getParent().resolve(sourceName + "-" + exchange + ".mdl");
    }

    public void getTodayData() {
        String url = this.eoddataIrl + "quote/list/" + this.exchange + "?apiKey=" + this.eoddataKey;
        String data = downloadEod(url, username, password);
        if (data == null || data.length() < 100) {
            throw new RuntimeException("Blank data from " + exchange);
        }
        parseEodAndSave(data);
    }
}
