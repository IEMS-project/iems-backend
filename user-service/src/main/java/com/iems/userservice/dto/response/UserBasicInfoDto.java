package com.iems.userservice.dto.response;

import java.util.UUID;

public record UserBasicInfoDto(
        UUID id,
        String fullName,
        String email,
        String image
) {}
