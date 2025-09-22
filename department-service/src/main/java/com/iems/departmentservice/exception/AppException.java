package com.iems.departmentservice.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final DepartmentErrorCode errorCode;

    public AppException(DepartmentErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(DepartmentErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}


