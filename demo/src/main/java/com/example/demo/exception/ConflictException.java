package com.example.demo.exception;

/**
 * Business-rule conflict — maps to HTTP 409 via GlobalExceptionHandler.
 * Used where a state transition or resource guard is violated (e.g. the
 * department canDelete gate, BACKEND_SPEC §7.4).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
