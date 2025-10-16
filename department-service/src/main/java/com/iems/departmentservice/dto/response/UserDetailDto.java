package com.iems.departmentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String address;
    private String phone;
    private String dob;
    private String gender;
    private String personalID;
    private String image;
    private String bankAccountNumber;
    private String bankName;
    private String contractType;
    private String startDate;
    private String role;
}

