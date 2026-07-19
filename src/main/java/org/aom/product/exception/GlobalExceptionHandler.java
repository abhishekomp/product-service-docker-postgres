package org.aom.product.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Global exception handler — intercepts exceptions thrown anywhere in the application
 * and converts them into structured JSON error responses.
 *
 * <p>Without this class, Spring would return a generic 500 Internal Server Error for
 * most exceptions — not useful for API consumers. This class maps specific exceptions
 * to the correct HTTP status codes and a consistent JSON body.
 *
 * <p>How it works:
 * When an exception bubbles up from a controller method, Spring checks if any
 * @ExceptionHandler method in any @RestControllerAdvice class handles that exception type.
 * If found, that handler runs instead of Spring's default error handling.
 */

// @RestControllerAdvice:
//   Combines @ControllerAdvice (applies to all controllers) and @ResponseBody
//   (all return values are serialised to JSON). It is the global "catch" for exceptions
//   from any @RestController in the application.
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles the case where a product is not found by its ID.
     *
     * <p>ProductService throws NoSuchElementException with a message like
     * "Product not found with id: 99". This handler catches it and returns
     * a 404 Not Found response with a structured JSON body.
     *
     * <p>Without this handler, Spring would return a 500 Internal Server Error —
     * technically wrong, because the server processed the request correctly,
     * it just could not find the resource.
     *
     * @param ex      the exception thrown by the service layer
     * @param request the incoming HTTP request (used to capture the request path)
     * @return a structured error response body
     */
    // @ExceptionHandler: tells Spring "when a NoSuchElementException is thrown
    // anywhere in a @RestController, call this method".
    @ExceptionHandler(NoSuchElementException.class)
    // @ResponseStatus: sets the HTTP response status code to 404 Not Found.
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),   // 404
                HttpStatus.NOT_FOUND.getReasonPhrase(), // "Not Found"
                ex.getMessage(),                // "Product not found with id: 99"
                request.getRequestURI()         // "/product-service/getProduct/99"
        );
    }

    /**
     * Handles validation failures from @Valid on @RequestBody parameters.
     *
     * <p>When the request body fails @NotBlank (or any other constraint), Spring throws
     * MethodArgumentNotValidException before the controller method is even called.
     * This handler catches it and returns a 400 Bad Request with a readable message
     * listing all the violated fields.
     *
     * <p>Example message: "pName: Product name must not be blank, skuCode: SKU code must not be blank"
     *
     * @param ex      the validation exception — contains details about every field that failed
     * @param request the incoming HTTP request
     * @return a structured error response body listing all validation failures
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // ex.getBindingResult().getFieldErrors() returns one FieldError per violated constraint.
        // We map each to "fieldName: violation message" and join them with ", ".
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),          // 400
                HttpStatus.BAD_REQUEST.getReasonPhrase(), // "Bad Request"
                message,
                request.getRequestURI()
        );
    }
}

