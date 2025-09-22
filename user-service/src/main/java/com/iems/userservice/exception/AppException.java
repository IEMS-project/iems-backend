package com.iems.userservice.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final UserErrorCode errorCode;

    public AppException(UserErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(UserErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}


