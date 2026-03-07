package com.iems.projectservice.service;

import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.dto.request.UserIdsDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IUserService {

    Optional<UserDetailDto> getUserById(UUID userId);

    List<UserDetailDto> getUsersByIds(UserIdsDto request);

    UserDetailDto convertToUserDetailDto(Map<String, Object> userData);

    UUID getUserIdFromRequest();
}
