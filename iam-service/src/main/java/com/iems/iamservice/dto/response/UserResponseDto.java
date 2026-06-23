package com.iems.iamservice.dto.response;

import com.iems.iamservice.entity.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String address;
    private String phone;
    private Date dob;
    private Gender gender;
    private String image;
}


