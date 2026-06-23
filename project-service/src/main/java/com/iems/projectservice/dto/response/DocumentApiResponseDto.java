package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentApiResponseDto<T> {
    private int statusCode;
    private String message;
    private T data;
}
