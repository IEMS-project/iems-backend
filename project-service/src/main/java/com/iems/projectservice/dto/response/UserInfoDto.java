package com.iems.projectservice.dto.response;

import java.util.UUID;

public record UserInfoDto(UUID id, String name, String email, String image) {
}
