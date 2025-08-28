package com.iems.iamservice.mapper;

import com.iems.iamservice.dto.response.PermissionResponseDto;
import com.iems.iamservice.dto.response.RoleResponseDto;
import com.iems.iamservice.dto.response.UserResponseDto;
import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.entity.Role;
import com.iems.iamservice.entity.UserAccount;

import java.util.Set;
import java.util.stream.Collectors;

public final class IamMapper {
    private IamMapper() {}

    public static UserResponseDto toUserResponse(UserAccount user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setEnabled(user.getEnabled());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRoles(user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()));
        return dto;
    }

    public static RoleResponseDto toRoleResponse(Role role) {
        RoleResponseDto dto = new RoleResponseDto();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        Set<String> permissions = role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
        dto.setPermissions(permissions);
        return dto;
    }

    public static PermissionResponseDto toPermissionResponse(Permission p) {
        PermissionResponseDto dto = new PermissionResponseDto();
        dto.setId(p.getId());
        dto.setCode(p.getCode());
        dto.setName(p.getName());
        return dto;
    }
}


