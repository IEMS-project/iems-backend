package com.iems.iamservice.dto.response;

import lombok.Data;

import java.util.UUID;

@Data
public class PermissionResponseDto {
    private UUID id;
    private String code;
    private String name;
}


