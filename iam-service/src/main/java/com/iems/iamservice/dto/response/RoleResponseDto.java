package com.iems.iamservice.dto.response;

import lombok.Data;

import java.util.Set;

@Data
public class RoleResponseDto {
    private Long id;
    private String code;
    private String name;
    private Set<String> permissions;
}


