package com.iems.projectservice.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final ProjectErrorCode errorCode;

    public AppException(ProjectErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(ProjectErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}


