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
    /**
     * Retrieves user information.
     *
     * @param accountId the account id parameter
     * @return an optional result when matching data is available
     */
    Optional<UserDetailDto> getUserById(UUID accountId);

    //Lay accountId hien tai
    /**
     * Retrieves user information.
     *
     * @return the get account id from request result
     */
    UUID getAccountIdFromRequest();

    //Lay fullname cua accountId
    /**
     * Resolves user information for the request.
     *
     * @param accountId the account id parameter
     * @return the resolve user name result
     */
    String resolveUserName(String accountId);
}
