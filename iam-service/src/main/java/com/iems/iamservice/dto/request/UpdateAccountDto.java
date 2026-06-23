package com.iems.iamservice.dto.request;

import lombok.Data;

import java.util.Set;

@Data
public class UpdateAccountDto {
    private String email;
    private Boolean enabled;
    private Set<String> roleCodes;
}


