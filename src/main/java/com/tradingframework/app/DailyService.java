package com.tradingframework.app;

import com.tradingframework.batchdata.EodDataSource;
import com.tradingframework.batchdata.PolygonDataSource;
import com.tradingframework.model.StockBarCollection;
import com.tradingframework.model.StockBarList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DailyService {
    public static void main(String[] args) {
        try {
            DailyService service = new DailyService();
            service.downloadEod();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void downloadEod() {
        long interval = 30000;
        try{
            AppConfig config = new AppConfig(Path.of(System.getProperty("config", "config/application.properties")));

            StockBarCollection polygon = downloadPolygon(List.of("2026-10-13"), config, interval);
            StockBarCollection nasdaq = downloadEoddata("NASDAQ", config, interval);
            StockBarCollection nyse = downloadEoddata("NYSE", config, interval);
            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private StockBarCollection downloadPolygon(List<String> dates, AppConfig config, long interval) throws InterruptedException {
        PolygonDataSource polygonDataSource = new PolygonDataSource(config);
        for (String date : dates) {
            polygonDataSource.downloadOneDay(date);
            Thread.sleep(interval);
        }

        StockBarCollection stockBarCollection = polygonDataSource.loadModel();
        polygonDataSource.parseAllFiles(stockBarCollection);
        polygonDataSource.saveModel(stockBarCollection);

        return stockBarCollection;
    }

    private StockBarCollection downloadEoddata(String exchange,AppConfig config, long interval) throws InterruptedException {
        EodDataSource eodDataSourceNasdaq = new EodDataSource(config, exchange);
        eodDataSourceNasdaq.getTodayData();
        Thread.sleep(interval);
        StockBarCollection stockBarCollection = eodDataSourceNasdaq.loadModel();
        eodDataSourceNasdaq.parseAllFiles(stockBarCollection);
        eodDataSourceNasdaq.saveModel(stockBarCollection);

        return stockBarCollection;
    }
}
