package com.iems.taskservice.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final TaskErrorCode errorCode;

    public AppException(TaskErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(TaskErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}


