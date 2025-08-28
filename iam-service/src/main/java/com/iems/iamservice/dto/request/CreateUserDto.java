package com.iems.iamservice.dto.request;

import lombok.Data;

import java.util.Set;

@Data
public class CreateUserDto {
    private String username;
    private String email;
    private String password;
    private Set<String> roleCodes;
}


