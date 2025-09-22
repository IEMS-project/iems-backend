package com.iems.documentservice.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final DocumentErrorCode errorCode;

    public AppException(DocumentErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(DocumentErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}


