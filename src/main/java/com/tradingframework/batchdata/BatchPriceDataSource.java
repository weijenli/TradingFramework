package com.tradingframework.batchdata;

import com.tradingframework.app.AppConfig;
import com.tradingframework.model.StockBar;
import com.tradingframework.model.StockBarCollection;
import com.tradingframework.storage.StockBarCollectionFile;
import org.h2.util.json.*;

import java.io.BufferedReader;
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
import java.time.LocalDate;
import java.util.stream.Stream;
import static com.tradingframework.model.StockBarList.YYYYMMDD_FORMATTER;

public abstract class BatchPriceDataSource {
    private static final System.Logger LOG = System.getLogger(BatchPriceDataSource.class.getName());
    protected final Path dir;
    protected final String sourceName;

    public BatchPriceDataSource(AppConfig config, String sourceName) {
        this.sourceName = sourceName;
        this.dir = Path.of(config.get("data.dir", "downloaded")).resolve(sourceName);
    }

    public void parseEodAndSave(String data) {
        String debugger = "";
        try {
            String yyyymmdd = null;
            JSONValue[] arr = getJsonArray(data);

            StringBuilder buf = new StringBuilder();
            for (JSONValue v : arr) {
                debugger = v.toString();
                String[] parsedStr = getOneLine((JSONObject) v, yyyymmdd);
                if (parsedStr != null) {
                    buf.append(parsedStr[0]);
                    yyyymmdd = parsedStr[1];
                }
            }

            String filename = getFilename(yyyymmdd);
            writeToFile(filename, "txt", buf.toString());
        }catch (Exception e){
            System.out.println(data);
            System.out.println("debug:" + debugger);
            e.printStackTrace();
        }
    }

    abstract JSONValue[] getJsonArray(String data);
    abstract String[] getOneLine(JSONObject o, String defaultDate);
    abstract String getFilename(String date);
    abstract Path getModelFile();

    protected void writeToFile(String filename, String fileExtension, String body) {
        try {
            LOG.log(System.Logger.Level.INFO, "Writing to " + dir + "/" + filename + "." + fileExtension);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(filename + "." + fileExtension), body);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not write file " + filename, e);
        }
    }

    public void parseAllFiles(StockBarCollection stockBarCollection) {
        try (Stream<Path> paths = Files.walk(this.dir)) {
            paths.filter(Files::isRegularFile) // Filter out directories, keep only regular files
                    .forEach(p -> loadCsv(p, stockBarCollection));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void loadCsv(Path file, StockBarCollection stockBarCollection) {
        if (skipFile(file.getFileName().toString()) ) return;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseCsv(line, stockBarCollection);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected boolean skipFile(String filename) {
        return filename.startsWith(".") || filename.contains("DS_Store");
    }

    private void parseCsv(String line, StockBarCollection stockBarCollection) throws Exception {
        String[] parts = line.split(",");

        String symbol = parts[0];
        LocalDate date = LocalDate.parse(parts[1], YYYYMMDD_FORMATTER);
        double open = Double.parseDouble(parts[2]);
        double high = Double.parseDouble(parts[3]);
        double low = Double.parseDouble(parts[4]);
        double close = Double.parseDouble(parts[5]);
        double volume = Double.parseDouble(parts[6]);
        stockBarCollection.addOne(new StockBar(symbol, date, open, high, low, close, volume));
    }

    public void saveModel(StockBarCollection stockBarCollection) {
        Path file = getModelFile();
        StockBarCollectionFile.save(stockBarCollection, file);
    }

    public StockBarCollection loadModel() {
        Path file = getModelFile();
        if (file.toFile().exists())
            return StockBarCollectionFile.load(file);
        else return new StockBarCollection();
    }

    public String downloadEod(String url, String username, String password) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password.toCharArray());
                        }
                    })
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(new URI(url))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected static String str(JSONObject o, String key) {
        return ((JSONString) o.getFirst(key)).getString();
    }

    protected static BigDecimal num(JSONObject o, String key) {
        return ((JSONNumber) o.getFirst(key)).getBigDecimal();
    }
}
