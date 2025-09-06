package com.iems.iamservice.dto.request;

import lombok.Data;

import java.util.Set;

@Data
public class UpdateRoleDto {
    private String name;
    private String description;
    private Set<String> permissionCodes;
}


