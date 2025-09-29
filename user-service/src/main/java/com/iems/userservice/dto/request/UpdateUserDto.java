package com.iems.userservice.dto.request;

import com.iems.userservice.entity.enums.ContractType;
import com.iems.userservice.entity.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserDto {
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
}
