package com.iems.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDto<T> {
    private int statusCode;    // trạng thái thành công hay thất bại
    private String message;     // thông báo
    private T data;             // dữ liệu trả về
}