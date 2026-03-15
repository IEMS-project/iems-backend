package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.response.ProjectMemberResponseDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final RoleRepository roleRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProjectMember addMemberToProject(UUID projectId, UUID accountId, UUID roleId) {
        if (projectMemberRepository.existsByProjectIdAndAccountId(projectId, accountId)) {
            throw new AppException(ProjectErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setAccountId(accountId);
        member.setRoleId(roleId);
        member.setJoinedAt(LocalDateTime.now());
        member.setAssignedByAccountId(accountId); // will be overridden by caller if needed
        return projectMemberRepository.save(member);
    }

    @Transactional
    public ProjectMember addMemberToProject(UUID projectId, UUID accountId, UUID roleId, UUID assignedBy) {
        if (projectMemberRepository.existsByProjectIdAndAccountId(projectId, accountId)) {
            throw new AppException(ProjectErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setAccountId(accountId);
        member.setRoleId(roleId);
        member.setJoinedAt(LocalDateTime.now());
        member.setAssignedByAccountId(assignedBy);
        return projectMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID accountId) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndAccountId(projectId, accountId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
        projectMemberRepository.delete(member);
    }

    @Transactional
    public ProjectMember updateMemberRole(UUID projectId, UUID accountId, UUID newRoleId) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndAccountId(projectId, accountId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
        member.setRoleId(newRoleId);
        return projectMemberRepository.save(member);
    }

    public List<ProjectMember> getProjectMembers(UUID projectId) {
        return projectMemberRepository.findByProjectId(projectId);
    }

    public boolean isProjectMember(UUID projectId, UUID accountId) {
        return projectMemberRepository.existsByProjectIdAndAccountId(projectId, accountId);
    }

    public ProjectMember getMember(UUID projectId, UUID accountId) {
        return projectMemberRepository.findByProjectIdAndAccountId(projectId, accountId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
    }

    public List<ProjectMemberResponseDto> getProjectMembersEnriched(UUID projectId) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        if (members.isEmpty())
            return List.of();

        // Collect all accountIds to batch-fetch (members + assignedBy)
        Set<UUID> accountIds = new HashSet<>();
        for (ProjectMember m : members) {
            accountIds.add(m.getAccountId());
            if (m.getAssignedByAccountId() != null) {
                accountIds.add(m.getAssignedByAccountId());
            }
        }

        // Batch-fetch users from IAM service: UserResponseDto.id == accountId
        Map<UUID, UserDetailDto> userMap = new HashMap<>();
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient
                    .getUsersByAccountIds(new AccountIdsDto(accountIds));
            if (response.getBody() != null && response.getBody().get("data") != null) {
                List<UserDetailDto> users = objectMapper.convertValue(
                        response.getBody().get("data"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));
                userMap = users.stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserDetailDto::getId, u -> u));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user details from IAM service: {}", e.getMessage());
        }

        // Fetch roles from local DB
        Set<UUID> roleIds = members.stream().map(ProjectMember::getRoleId).collect(Collectors.toSet());
        Map<UUID, String> roleNameMap = roleRepository.findAllById(roleIds)
                .stream().collect(Collectors.toMap(Role::getId, Role::getName));

        final Map<UUID, UserDetailDto> finalUserMap = userMap;
        return members.stream().map(m -> {
            UserDetailDto user = finalUserMap.get(m.getAccountId());
            UserDetailDto assignedBy = finalUserMap.get(m.getAssignedByAccountId());

            return new ProjectMemberResponseDto(
                    m.getId(),
                    m.getAccountId(),
                    user != null ? trim(user.getFirstName()) + " " + trim(user.getLastName()) : null,
                    user != null ? user.getEmail() : null,
                    user != null ? user.getImage() : null,
                    m.getRoleId(),
                    roleNameMap.get(m.getRoleId()),
                    m.getStatus(),
                    m.getJoinedAt(),
                    m.getAssignedByAccountId(),
                    assignedBy != null ? trim(assignedBy.getFirstName()) + " " + trim(assignedBy.getLastName()) : null);
        }).collect(Collectors.toList());
    }

    private String trim(String s) {
        return s != null ? s.trim() : "";
    }
}
