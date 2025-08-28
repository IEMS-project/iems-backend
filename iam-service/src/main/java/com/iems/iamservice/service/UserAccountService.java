package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreateUserDto;
import com.iems.iamservice.dto.request.UpdateUserDto;
import com.iems.iamservice.entity.Role;
import com.iems.iamservice.entity.UserAccount;
import com.iems.iamservice.repository.RoleRepository;
import com.iems.iamservice.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserAccount create(CreateUserDto dto) {
        if (userAccountRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userAccountRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Set<Role> roles = fetchRoles(dto.getRoleCodes());

        UserAccount user = UserAccount.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .enabled(true)
                .createdAt(Instant.now())
                .roles(roles)
                .build();

        return userAccountRepository.save(user);
    }

    public List<UserAccount> findAll() {
        return userAccountRepository.findAll();
    }

    public UserAccount findById(Long id) {
        return userAccountRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional
    public UserAccount update(Long id, UpdateUserDto dto) {
        UserAccount user = findById(id);
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getEnabled() != null) {
            user.setEnabled(dto.getEnabled());
        }
        if (dto.getRoleCodes() != null) {
            user.setRoles(fetchRoles(dto.getRoleCodes()));
        }
        return userAccountRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        userAccountRepository.deleteById(id);
    }

    private Set<Role> fetchRoles(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        return codes.stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + code)))
                .collect(Collectors.toSet());
    }
}


