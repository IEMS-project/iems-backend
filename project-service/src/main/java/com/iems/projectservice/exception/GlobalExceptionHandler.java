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
                errorCode.getMessage(),
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<String>> handleGlobalException(Exception ex, WebRequest request) throws Exception {
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


