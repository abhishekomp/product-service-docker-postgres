package org.aom.product.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP payload logging filter — logs every incoming request and outgoing response.
 *
 * <p>Runs once per HTTP request (guaranteed by OncePerRequestFilter) and captures:
 * <ul>
 *   <li>Incoming: HTTP method, URI, query string, Content-Type, request body</li>
 *   <li>Outgoing: HTTP status, duration in ms, response body</li>
 * </ul>
 *
 * <p>Each request is tagged with a unique requestId (UUID) so you can trace a
 * request through the logs even when multiple requests arrive concurrently.
 *
 * <p>⚠ Important: this filter logs the FULL request and response body.
 * In production, be careful not to log sensitive data such as passwords,
 * credit card numbers, or personal information. See the Payload-Logging.md
 * document for guidance on sanitising log output.
 */

// @Component: registers this class as a Spring bean.
// Spring Boot automatically registers all Filter beans into the servlet filter chain.
@Component
public class PayloadLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(PayloadLoggingFilter.class);

    // Maximum number of body characters to log.
    // Bodies larger than this are truncated to avoid flooding the log file.
    private static final int MAX_BODY_LOG_SIZE = 2000;

    /**
     * OncePerRequestFilter guarantees this method is called exactly once per request,
     * even in a forward/include scenario. It is the correct base class for logging filters.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // --- Request wrapping ---
        // HttpServletRequest body is an InputStream — it can only be read ONCE.
        // If we read it here for logging, the controller would receive an empty body.
        // ContentCachingRequestWrapper solves this by buffering the body internally
        // so it can be read multiple times.
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);

        // ContentCachingResponseWrapper does the same for the response body.
        // Without this, reading the response body here would consume it and the
        // client would receive an empty response.
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        // --- Correlation ID (requestId) ---
        // MDC (Mapped Diagnostic Context) is a per-thread key-value store that Logback
        // reads and includes in every log line for this thread.
        // By storing a unique requestId here, every log line produced during this request
        // (even deep in the service or repository layers) will include the same requestId.
        // This lets you find all log lines for one request with a simple grep.
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);

        long startTime = System.currentTimeMillis();

        try {
            // Let the request proceed through the rest of the filter chain
            // and ultimately reach the controller.
            // The body is read and buffered inside the wrappers during this call.
            filterChain.doFilter(requestWrapper, responseWrapper);

        } finally {
            long durationMs = System.currentTimeMillis() - startTime;

            // Log the request AFTER the filter chain — at this point the body
            // has been read by the controller and is available in the wrapper's buffer.
            logRequest(requestWrapper);

            // Log the response — the body is buffered in responseWrapper.
            logResponse(responseWrapper, durationMs);

            // ⚠ CRITICAL: copy the buffered response body back to the real response.
            // Without this line, the client receives an empty body because
            // ContentCachingResponseWrapper intercepted and buffered it.
            responseWrapper.copyBodyToResponse();

            // Clean up MDC to avoid requestId leaking into the next request
            // on the same thread (thread pool reuses threads).
            MDC.clear();
        }
    }

    /**
     * Logs the incoming HTTP request: method, URI, Content-Type, and body.
     */
    private void logRequest(ContentCachingRequestWrapper request) {
        String body = readBody(request.getContentAsByteArray(), request.getCharacterEncoding());
        String queryString = request.getQueryString();
        String uri = queryString != null
                ? request.getRequestURI() + "?" + queryString
                : request.getRequestURI();

        logger.info(
                ">> INCOMING REQUEST  | {} {} | Content-Type: {} | Body: {}",
                request.getMethod(),
                uri,
                request.getContentType() != null ? request.getContentType() : "none",
                body
        );
    }

    /**
     * Logs the outgoing HTTP response: status code, duration, and body.
     */
    private void logResponse(ContentCachingResponseWrapper response, long durationMs) {
        String body = readBody(response.getContentAsByteArray(), response.getCharacterEncoding());

        logger.info(
                "<< OUTGOING RESPONSE | Status: {} | Duration: {}ms | Body: {}",
                response.getStatus(),
                durationMs,
                body
        );
    }

    /**
     * Converts a byte array to a string, truncating if it exceeds MAX_BODY_LOG_SIZE.
     *
     * @param bodyBytes       the raw bytes of the body
     * @param characterEncoding the encoding declared on the request/response
     * @return a readable string representation of the body
     */
    private String readBody(byte[] bodyBytes, String characterEncoding) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return "<empty>";
        }

        String encoding = (characterEncoding != null) ? characterEncoding : StandardCharsets.UTF_8.name();
        String body;
        try {
            body = new String(bodyBytes, encoding);
        } catch (Exception e) {
            return "<unreadable binary body>";
        }

        // Truncate very large bodies to avoid enormous log lines
        if (body.length() > MAX_BODY_LOG_SIZE) {
            return body.substring(0, MAX_BODY_LOG_SIZE) + "... [TRUNCATED at " + MAX_BODY_LOG_SIZE + " chars]";
        }

        return body;
    }
}

