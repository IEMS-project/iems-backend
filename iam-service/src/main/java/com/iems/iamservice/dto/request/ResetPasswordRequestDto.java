package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequestDto {

    @NotBlank(message = "New password cannot be blank")
    private String newPassword;
}


