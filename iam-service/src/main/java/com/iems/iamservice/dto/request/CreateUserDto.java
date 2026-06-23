package com.iems.iamservice.dto.request;

import com.iems.iamservice.entity.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserDto {
    @NotBlank(message = "First name is required")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    private String lastName;
    
    @Email(message = "Email should be valid")
    private String email;
    
    private String address;
    
    @Pattern(regexp = "^\\d{10,11}$", message = "Phone number must be 10-11 digits without spaces or special characters")
    private String phone;
    
    @PastOrPresent(message = "Date of birth cannot be in the future")
    private Date dob;
    
    private Gender gender;
    
    private String image;
    
    // Thêm các trường cho việc tạo account
    private String username;
    private String password;
    private java.util.Set<String> roleCodes;
}


