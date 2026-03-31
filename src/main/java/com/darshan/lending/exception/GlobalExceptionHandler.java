package com.darshan.lending.exception;

import com.darshan.lending.dto.ApiResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ───────────── Resource Not Found ─────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ───────────── Business Exceptions ─────────────

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ───────────── Validation Errors ─────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    // ───────────── Invalid JSON Body (Date / Enum / Format) ─────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleJsonParseError(
            HttpMessageNotReadableException ex) {

        Map<String, String> errors = new HashMap<>();

        Throwable cause = ex.getCause();

        if (cause instanceof InvalidFormatException ife) {

            String fieldName = ife.getPath().isEmpty()
                    ? "field"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();

            // Date parsing error
            if (ife.getTargetType() != null &&
                    ife.getTargetType().equals(LocalDate.class)) {

                errors.put(fieldName,
                        "Invalid date format. Expected format: YYYY-MM-DD (Example: 2000-05-15)");

            }
            // Enum parsing error
            else if (ife.getTargetType() != null &&
                    ife.getTargetType().isEnum()) {

                errors.put(fieldName,
                        "Invalid value '" + ife.getValue() +
                                "'. Allowed values: " +
                                Arrays.toString(ife.getTargetType().getEnumConstants()));
            }
            // Other type errors
            else {

                errors.put(fieldName,
                        "Invalid value '" + ife.getValue() +
                                "' for field '" + fieldName + "'");
            }

        } else {

            errors.put("request", "Malformed JSON request body. Please check your input.");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Invalid request format")
                        .data(errors)
                        .build());
    }

    // ───────────── Generic Exception (Fallback) ─────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("UNEXPECTED ERROR: ", ex); // ADD THIS LINE
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ex.getMessage())); // CHANGE TO ex.getMessage()
    }

    // ── 401 Unauthorized ──────────────────────────────────────────────
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Unauthorized: " + ex.getMessage()));
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied. You don't have permission."));
    }

    // ── 400 Wrong path variable type (e.g. /loans/abc instead of /loans/1) ──
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid parameter '" + ex.getName()
                        + "': expected " + ex.getRequiredType().getSimpleName()));
    }

    // ── 400 Missing required query parameter ──────────────────────────
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Required parameter missing: " + ex.getParameterName()));
    }
}