package com.iems.userservice.dto.request;

import com.iems.userservice.entity.enums.Gender;
import com.iems.userservice.entity.enums.ContractType;
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
    private String bankAccountNumber;
    private String bankName;
    private ContractType contractType;
    private Date startDate;
    private String role;
    
    // Thêm các trường cho việc tạo account
    private String username;
    private String password;
    private java.util.Set<String> roleCodes;
}
