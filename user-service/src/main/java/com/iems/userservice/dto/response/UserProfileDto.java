package com.iems.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String image;
    private String role;
}
