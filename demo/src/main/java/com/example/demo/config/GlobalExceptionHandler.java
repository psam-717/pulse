package com.example.demo.config;

import com.example.demo.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Validation failures from @Valid (missing/blank fields, bad email, etc.)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "Validation failed", errors));
    }

    // Bad JSON body or invalid value format
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> handleBadBody(HttpMessageNotReadableException ex) {
        String msg = "Invalid request body. Check that all values are the correct type.";

        // Try to extract field name from Jackson 3.x exception path
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife) {
            var path = ife.getPath();
            if (path != null && !path.isEmpty()) {
                String fieldName = path.get(0).toString();
                msg = "Invalid value for field '" + fieldName
                        + "'. Expected type: " + ife.getTargetType().getSimpleName();
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, msg));
    }

    // Missing query/request params
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "Missing required parameter: " + ex.getParameterName()));
    }

    // Type mismatch in path variables or query params (e.g., string instead of Long)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "Invalid value for '" + ex.getName()
                        + "'. Expected: " + ex.getRequiredType().getSimpleName()));
    }

    // No auth token or bad token (caught by the filter, but keep this as fallback)
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiResponse> handleAuth(org.springframework.security.core.AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "Authentication required. Provide a valid Bearer token in the Authorization header."));
    }

    // Insufficient permissions
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, "Access denied. You don't have permission to perform this action."));
    }

    // Business logic errors from services
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    // Database constraint violations (duplicate entry, foreign key, etc.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        String msg = "A database constraint was violated. This is likely a duplicate entry or a missing reference.";

        // Extract the root cause message for a more specific error
        Throwable cause = ex.getRootCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null) {
                if (causeMsg.contains("duplicate") || causeMsg.contains("unique") || causeMsg.contains("already exists")) {
                    msg = "This record already exists. Please check for duplicates.";
                } else if (causeMsg.contains("foreign key") || causeMsg.contains("violates foreign")) {
                    msg = "Cannot perform this operation. A related record is still using this data.";
                }
            }
        }

        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, msg));
    }

    // Catch-all for unhandled errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "An unexpected error occurred. Please try again later."));
    }
}