package com.iems.iamservice.exception;



import com.iems.iamservice.dto.ApiResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

@ControllerAdvice
public class GlobalExceptionHandler {
    //Xử lý ngoại lệ Custom
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponseDto<String>> handleAppException(AppException ex, WebRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        ApiResponseDto<String> response = new ApiResponseDto<>(errorCode.getHttpStatus().name(), errorCode.getMessage(), null);
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }
    // Xử lý ngoại lệ IllegalArgumentException (ví dụ: username đã tồn tại)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<String>> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        ApiResponseDto<String> response = new ApiResponseDto<>(HttpStatus.BAD_REQUEST.name(), "Bad request", null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // Xử lý ngoại lệ khi không tìm thấy tài nguyên (NoSuchElementException)
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponseDto<String>> handleNoSuchElementException(NoSuchElementException ex, WebRequest request) {
        ApiResponseDto<String> response = new ApiResponseDto<>(HttpStatus.NOT_FOUND.name(), "Resource not found", null);
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }


    // Xử lý IllegalStateException (ví dụ: không tìm thấy user trong SecurityContext)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponseDto<String>> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        ApiResponseDto<String> response = new ApiResponseDto<>(HttpStatus.UNAUTHORIZED.name(), "Unauthorized", null);
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    //Xử lý validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        ApiResponseDto<Map<String, String>> response = new ApiResponseDto<>(HttpStatus.BAD_REQUEST.name(), "Validation failed", null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // Xử lý ngoại lệ xác thực Spring Security
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponseDto<String>> handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        ApiResponseDto<String> response = new ApiResponseDto<>(ErrorCode.INVALID_CREDENTIALS.getHttpStatus().name(), "Invalid credentials", null);
        return new ResponseEntity<>(response, ErrorCode.INVALID_CREDENTIALS.getHttpStatus());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponseDto<String>> handleDisabledException(DisabledException ex, WebRequest request) {
        ApiResponseDto<String> response = new ApiResponseDto<>(ErrorCode.ACCOUNT_LOCKED.getHttpStatus().name(), "Account is locked", null);
        return new ResponseEntity<>(response, ErrorCode.ACCOUNT_LOCKED.getHttpStatus());
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponseDto<String>> handleLockedException(LockedException ex, WebRequest request) {
        ApiResponseDto<String> response = new ApiResponseDto<>(ErrorCode.ACCOUNT_LOCKED.getHttpStatus().name(), "Account is locked", null);
        return new ResponseEntity<>(response, ErrorCode.ACCOUNT_LOCKED.getHttpStatus());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDto<String>> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        ApiResponseDto<String> response = new ApiResponseDto<>(ErrorCode.UNAUTHORIZED.getHttpStatus().name(), "Forbidden", null);
        return new ResponseEntity<>(response, ErrorCode.UNAUTHORIZED.getHttpStatus());
    }

    // Xử lý ngoại lệ cơ sở dữ liệu
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseDto<String>> handleDataIntegrityViolationException(DataIntegrityViolationException ex, WebRequest request) {
        ApiResponseDto<String> response = new ApiResponseDto<>(ErrorCode.DATABASE_ERROR.getHttpStatus().name(), "Data integrity error", null);
        return new ResponseEntity<>(response, ErrorCode.DATABASE_ERROR.getHttpStatus());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponseDto<String>> handleDataAccessException(DataAccessException ex, WebRequest request) {
        ApiResponseDto<String> response = new ApiResponseDto<>(ErrorCode.DATABASE_ERROR.getHttpStatus().name(), "Database error", null);
        return new ResponseEntity<>(response, ErrorCode.DATABASE_ERROR.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<String>> handleGlobalException(Exception ex, WebRequest request) throws Exception {
        String path = request.getDescription(false);

        // Ignore Swagger endpoints, let Springdoc handle them
        if (path.contains("/api-docs") || path.contains("/swagger-ui")) {
            throw ex;
        }

        ApiResponseDto<String> response = new ApiResponseDto<>(
                ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus().name(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                null
        );
        return new ResponseEntity<>(response, ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
    }



}