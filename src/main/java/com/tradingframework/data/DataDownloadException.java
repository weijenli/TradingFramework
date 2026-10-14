package com.tradingframework.data;

public class DataDownloadException extends RuntimeException {

    public DataDownloadException(String message) {
        super(message);
    }

    public DataDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
