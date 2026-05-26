package com.iems.iamservice.dto.response;

import com.iems.iamservice.entity.enums.Gender;
import com.iems.iamservice.entity.enums.SubscriptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccountResponseDto {
    private UUID id;
    private UUID userId;
    private String username;
    private String email;
    private Boolean enabled;
    private Instant createdAt;
    private Set<String> roles;
    private SubscriptionType subscriptionType;
    private Instant premiumUntil;

    private UUID profileId;
    private String firstName;
    private String lastName;
    private String displayName;
    private String address;
    private String phone;
    private Date dob;
    private Gender gender;
    private String image;
}
