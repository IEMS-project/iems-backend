package com.iems.iamservice.dto.request;

import com.iems.iamservice.entity.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserDto {
    private String firstName;
    private String lastName;
    private String email;
    private String address;
    private String phone;
    private Date dob;
    private Gender gender;
    private String image;
    
    // Thêm các trường cho việc tạo account
    private String username;
    private String password;
    private java.util.Set<String> roleCodes;
}


