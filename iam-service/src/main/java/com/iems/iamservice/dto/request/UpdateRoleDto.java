package com.iems.iamservice.dto.request;

import lombok.Data;

import java.util.Set;

@Data
public class UpdateRoleDto {
    private String name;
    private Set<String> permissionCodes;
}


