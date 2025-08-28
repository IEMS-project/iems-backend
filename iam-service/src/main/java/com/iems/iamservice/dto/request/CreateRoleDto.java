package com.iems.iamservice.dto.request;

import lombok.Data;

import java.util.Set;

@Data
public class CreateRoleDto {
    private String code;
    private String name;
    private Set<String> permissionCodes;
}


