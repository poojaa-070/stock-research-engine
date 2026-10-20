package com.stockresearch.exception;

/** The scraper service, Screener or NSE failed or was unreachable (HTTP 502). */
public class UpstreamException extends RuntimeException {
    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
