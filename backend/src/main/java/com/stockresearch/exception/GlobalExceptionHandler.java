package com.stockresearch.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * Turns exceptions into a consistent JSON error body with a meaningful status
 * code, instead of Spring's default 500 for everything. The frontend shows
 * `message` directly, so messages are written for the person reading them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(int status, String error, String message, String path, LocalDateTime timestamp) {}

    @ExceptionHandler({NotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, e.getMessage() != null ? e.getMessage() : "Not found", req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException e, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, e.getMessage(), req);
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badRequest(Exception e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), req);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> upstreamResponse(WebClientResponseException e, HttpServletRequest req) {
        // A 404 from the scraper's /import means "Screener has no such company" —
        // that's a genuine not-found for the user, not a gateway failure.
        if (e.getStatusCode().value() == 404) {
            return build(HttpStatus.NOT_FOUND, "No matching company was found on Screener.in.", req);
        }
        log.warn("Scraper service returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        return build(HttpStatus.BAD_GATEWAY,
                "The scraper service returned an error (" + e.getStatusCode().value() + ").", req);
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiError> upstreamUnreachable(WebClientRequestException e, HttpServletRequest req) {
        return build(HttpStatus.BAD_GATEWAY,
                "The scraper service isn't reachable. Start it with: uvicorn api_server:app --port 8000", req);
    }

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<ApiError> upstream(UpstreamException e, HttpServletRequest req) {
        return build(HttpStatus.BAD_GATEWAY, e.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error. Check the backend logs for details.", req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(
                new ApiError(status.value(), status.getReasonPhrase(), message, req.getRequestURI(), LocalDateTime.now()));
    }
}
