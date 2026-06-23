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
public class OAuthIdTokenRequestDto {

    @NotBlank(message = "Google ID token cannot be blank")
    private String idToken;
}