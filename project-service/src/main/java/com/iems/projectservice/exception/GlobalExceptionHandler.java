package com.iems.projectservice.exception;

import com.iems.projectservice.dto.response.ApiResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponseDto<String>> handleAppException(AppException ex, WebRequest request) {
        ProjectErrorCode errorCode = ex.getErrorCode();
        ApiResponseDto<String> response = new ApiResponseDto<>(
                errorCode.getHttpStatus().name(),
                // Use the custom message if provided, else fall back to the error code default
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null
        );
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        ApiResponseDto<String> response = new ApiResponseDto<>(HttpStatus.BAD_REQUEST.name(), ex.getMessage(), null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage(),
                        (msg1, msg2) -> msg1
                ));

        ApiResponseDto<Map<String, String>> response = new ApiResponseDto<>(
                HttpStatus.BAD_REQUEST.name(),
                "Validation failed",
                errors
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Catch-all handler.
     * IMPORTANT: if the exception is actually an AppException that was caught and
     * re-thrown by a controller's generic "catch (Exception e)" block, we re-throw it
     * here so the handleAppException method above processes it with the correct HTTP status
     * (e.g. 402 for PREMIUM_REQUIRED) instead of collapsing everything to 500/400.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<String>> handleGlobalException(Exception ex, WebRequest request) throws Exception {
        // Let AppException reach its dedicated handler
        if (ex instanceof AppException) {
            throw ex;
        }

        String path = request.getDescription(false);
        if (path.contains("/api-docs") || path.contains("/swagger-ui")) {
            throw ex;
        }
        ApiResponseDto<String> response = new ApiResponseDto<>(
                ProjectErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus().name(),
                ProjectErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                null
        );
        return new ResponseEntity<>(response, ProjectErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
    }
}


