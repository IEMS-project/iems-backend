package com.iems.userservice.dto.request;

import com.iems.userservice.entity.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRequestDto {
    private String firstName;
    private String lastName;
    private String email;
    private String address;
    private String phone;
    private Date dob;
    private Gender gender;
    private String personalID;
    private String image;
}
