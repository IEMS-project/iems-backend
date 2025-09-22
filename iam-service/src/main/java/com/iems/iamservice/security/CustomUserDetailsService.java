package com.iems.iamservice.security;

import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.entity.Role;
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

import java.util.HashSet;
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
        
        try {
            Account user = accountRepository.findByUsernameOrEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            log.debug("Found user: {} with userId: {}", user.getUsername(), user.getUserId());

            // Create authorities from roles
            Set<SimpleGrantedAuthority> authorities = new HashSet<>();
            
            try {
                Set<Role> userRoles = userRolePermissionService.getUserRoles(user.getUserId());
                log.debug("User {} has {} roles", username, userRoles.size());
                
                userRoles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                        .forEach(authorities::add);
            } catch (Exception e) {
                log.warn("Failed to load roles for user {}: {}", username, e.getMessage());
                // Continue without roles
            }

            // Add direct permissions
            try {
                Set<Permission> userPermissions = userRolePermissionService.getAllUserPermissions(user.getUserId());
                log.debug("User {} has {} permissions", username, userPermissions.size());
                
                userPermissions.stream()
                        .map(permission -> new SimpleGrantedAuthority(permission.getCode()))
                        .forEach(authorities::add);
            } catch (Exception e) {
                log.warn("Failed to load permissions for user {}: {}", username, e.getMessage());
                // Continue without permissions
            }

            log.debug("User {} loaded with {} authorities", username, authorities.size());
            
            return new User(user.getUsername(), user.getPasswordHash(), user.getEnabled(), 
                    true, true, true, authorities);
                    
        } catch (Exception e) {
            log.error("Failed to load user {}: {}", username, e.getMessage(), e);
            throw new UsernameNotFoundException("User not found: " + username, e);
        }
    }
}
