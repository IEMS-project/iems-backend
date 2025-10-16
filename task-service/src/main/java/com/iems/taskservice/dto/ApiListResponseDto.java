package com.iems.taskservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiListResponseDto<T> {
    private String status;
    private String message;
    private int count;
    private T data;
}


