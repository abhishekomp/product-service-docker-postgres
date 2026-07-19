package org.aom.product.exception;

import java.time.LocalDateTime;

/**
 * Structured JSON body returned for all error responses.
 *
 * <p>Using a Java Record (introduced in Java 16, available in Java 21) — a concise
 * way to define an immutable data carrier class. The compiler generates the
 * constructor, getters, equals(), hashCode(), and toString() automatically.
 *
 * <p>Example JSON output for a 404:
 * <pre>
 * {
 *   "timestamp": "2024-08-16T10:30:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Product not found with id: 99",
 *   "path": "/product-service/getProduct/99"
 * }
 * </pre>
 *
 * <p>Having a consistent error shape means API consumers can always parse errors
 * the same way, regardless of what went wrong.
 */
public record ErrorResponse(

        // The date and time the error occurred — useful for debugging and log correlation.
        LocalDateTime timestamp,

        // The HTTP status code as an integer (e.g. 404, 400, 500).
        int status,

        // A short human-readable label matching the HTTP status (e.g. "Not Found", "Bad Request").
        String error,

        // The specific message explaining what went wrong (e.g. "Product not found with id: 99").
        String message,

        // The request path that triggered the error (e.g. "/product-service/getProduct/99").
        String path
) {
}

