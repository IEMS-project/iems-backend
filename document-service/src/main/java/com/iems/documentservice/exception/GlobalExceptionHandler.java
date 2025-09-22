package com.iems.documentservice.exception;

import com.iems.documentservice.dto.response.ApiResponseDto;
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
        DocumentErrorCode errorCode = ex.getErrorCode();
        ApiResponseDto<String> response = new ApiResponseDto<>(
                errorCode.getHttpStatus().value(),
                errorCode.getMessage(),
                null
        );
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        ApiResponseDto<String> response = new ApiResponseDto<>(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), null);
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
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<String>> handleGlobalException(Exception ex, WebRequest request) throws Exception {
        String path = request.getDescription(false);
        if (path.contains("/api-docs") || path.contains("/swagger-ui")) {
            throw ex;
        }
        ApiResponseDto<String> response = new ApiResponseDto<>(
                DocumentErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus().value(),
                DocumentErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                null
        );
        return new ResponseEntity<>(response, DocumentErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
    }
}


