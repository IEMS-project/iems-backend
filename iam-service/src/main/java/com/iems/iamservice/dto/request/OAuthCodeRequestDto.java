package com.iems.iamservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthCodeRequestDto {

    @NotBlank(message = "OAuth code cannot be blank")
    private String code;
}
