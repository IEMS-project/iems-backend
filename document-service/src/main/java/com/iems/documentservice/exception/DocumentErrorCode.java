package com.iems.documentservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DocumentErrorCode {
    INVALID_REQUEST("Invalid request", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    FILE_NOT_FOUND("File not found", HttpStatus.NOT_FOUND),
    FOLDER_NOT_FOUND("Folder not found", HttpStatus.NOT_FOUND),
    PERMISSION_DENIED("Permission denied", HttpStatus.FORBIDDEN),
    WRITE_PERMISSION_REQUIRED("Bạn chỉ có quyền xem. Cần quyền chỉnh sửa để thực hiện thao tác này", HttpStatus.FORBIDDEN),
    UPLOAD_FAILED("File upload failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus httpStatus;
}


