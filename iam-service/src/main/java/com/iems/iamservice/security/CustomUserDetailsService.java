package com.iems.iamservice.security;

import com.iems.iamservice.entity.Account;
import com.iems.iamservice.repository.AccountRepository;
import com.iems.iamservice.service.UserRolePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom UserDetailsService to avoid circular dependency
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Autowired
    private UserRolePermissionService userRolePermissionService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);
        
        Account user = accountRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Create authorities from roles
        Set<SimpleGrantedAuthority> authorities = userRolePermissionService.getUserRoles(user.getUserId()).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                .collect(Collectors.toSet());

        // Add direct permissions
        userRolePermissionService.getAllUserPermissions(user.getUserId()).stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getCode()))
                .forEach(authorities::add);

        log.debug("User {} loaded with {} authorities", username, authorities.size());
        
        return new User(user.getUsername(), user.getPasswordHash(), user.getEnabled(), 
                true, true, true, authorities);
    }
}
