package com.iems.chatservice.service;

import com.iems.chatservice.dto.UserDetailDto;
import com.iems.chatservice.security.JwtUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IUserService {


    //Lay tat ca thong tin ve user thong qua id
    Optional<UserDetailDto> getUserById(UUID accountId);

    //Lay accountId hien tai
    UUID getAccountIdFromRequest();

    //Lay fullname cua accountId
    String resolveUserName(String accountId);
}
