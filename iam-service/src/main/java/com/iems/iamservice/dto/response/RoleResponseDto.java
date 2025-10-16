package com.iems.iamservice.dto.response;

import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class RoleResponseDto {
    private UUID id;
    private String code;
    private String name;
    private Set<String> permissions;
}


