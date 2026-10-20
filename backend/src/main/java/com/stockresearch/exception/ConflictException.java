package com.stockresearch.exception;

/** The request is valid but the current state can't satisfy it yet (HTTP 409),
 * e.g. "what changed?" before two reports exist. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
