package com.iems.taskservice.service;

import com.iems.taskservice.dto.UserDetailDto;
import com.iems.taskservice.dto.UserIdsDto;

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
