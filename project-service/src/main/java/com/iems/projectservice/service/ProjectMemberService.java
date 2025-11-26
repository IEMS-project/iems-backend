package com.iems.projectservice.service;

import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.UserIdsDto;
import com.iems.projectservice.dto.response.ProjectMemberResponseDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.ProjectAllowedRole;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.entity.enums.MemberStatus;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberService {
    
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAllowedRoleService projectAllowedRoleService;

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();
        return userId;
    }

    private Optional<UserDetailDto> getUserById(UUID userId) {
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUserById(userId);

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return Optional.of(convertToUserDetailDto(userData));
            }

            return Optional.empty();
        } catch (Exception e) {
            // Log error and return empty
            System.err.println("Error fetching user " + userId + " from User Service: " + e.getMessage());
            return Optional.empty();
        }
    }
    private UserDetailDto convertToUserDetailDto(Map<String, Object> userData) {
        UserDetailDto dto = new UserDetailDto();
        dto.setId(UUID.fromString(userData.get("id").toString()));
        dto.setFirstName((String) userData.get("firstName"));
        dto.setLastName((String) userData.get("lastName"));
        dto.setEmail((String) userData.get("email"));
        dto.setAddress((String) userData.get("address"));
        dto.setPhone((String) userData.get("phone"));

        // Handle Date objects - convert to string
        Object dob = userData.get("dob");
        dto.setDob(dob != null ? dob.toString() : null);

        // Handle enum objects - convert to string
        Object gender = userData.get("gender");
        dto.setGender(gender != null ? gender.toString() : null);

        dto.setPersonalID((String) userData.get("personalID"));
        dto.setImage((String) userData.get("image"));
        dto.setBankAccountNumber((String) userData.get("bankAccountNumber"));
        dto.setBankName((String) userData.get("bankName"));

        // Handle enum objects - convert to string
        Object contractType = userData.get("contractType");
        dto.setContractType(contractType != null ? contractType.toString() : null);

        // Handle Date objects - convert to string
        Object startDate = userData.get("startDate");
        dto.setStartDate(startDate != null ? startDate.toString() : null);

        dto.setRole((String) userData.get("role"));
        return dto;
    }


    public ProjectMemberResponseDto addMemberToProject(UUID projectId, UUID userId, UUID roleId) {
        log.info("Adding member to project: projectId={}, userId={}, roleId={}", projectId, userId, roleId);
        
        // Execute transaction logic separately
        UUID savedMemberId = saveOrUpdateMember(projectId, userId, roleId);
        
        // Fetch and map after transaction is committed
        ProjectMember savedMember = projectMemberRepository.findById(savedMemberId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
        
        return mapToProjectMemberResponseDto(savedMember, new HashMap<>());
    }
    
    @Transactional
    private UUID saveOrUpdateMember(UUID projectId, UUID userId, UUID roleId) {
        UUID assignedBy = getUserIdFromRequest();
        
        // Validate project exists
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        
        // Check if user is already a member
        Optional<ProjectMember> existingMember = projectMemberRepository.findMemberByProjectAndUser(projectId, userId);
        
        if (existingMember.isPresent()) {
            // Update existing member
            log.info("Member already exists, updating role: projectId={}, userId={}", projectId, userId);
            ProjectMember member = existingMember.get();
            member.setRoleId(roleId);
            member.setAssignedBy(assignedBy);
            projectMemberRepository.save(member);
            return member.getId();
        } else {
            // Create new member
            ProjectMember projectMember = new ProjectMember();
            projectMember.setProject(project);
            projectMember.setUserId(userId);
            projectMember.setRoleId(roleId);
            projectMember.setStatus(MemberStatus.ACTIVE); // Set default status
            projectMember.setJoinedAt(LocalDateTime.now());
            projectMember.setAssignedBy(assignedBy);
            
            projectMemberRepository.save(projectMember);
            return projectMember.getId();
        }
    }
    
    public List<ProjectMemberResponseDto> getProjectMembers(UUID projectId) {
        log.info("Getting project members: projectId={}", projectId);
        
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        Map<UUID, UserDetailDto> userMap = getUserMapFromMembers(members);
        return members.stream()
                .map(member -> mapToProjectMemberResponseDto(member, userMap))
                .collect(Collectors.toList());
    }
    
    @Transactional
    public ProjectMemberResponseDto updateMemberRole(UUID projectId, UUID userId, UUID newRoleId) {
        log.info("Updating member role: projectId={}, userId={}, newRoleId={}", projectId, userId, newRoleId);
        
        ProjectMember projectMember = projectMemberRepository.findMemberByProjectAndUser(projectId, userId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
        
        projectMember.setRoleId(newRoleId);
        ProjectMember updatedMember = projectMemberRepository.save(projectMember);
        
        return mapToProjectMemberResponseDto(updatedMember, new HashMap<>());
    }
    
    @Transactional
    public ProjectMemberResponseDto updateMemberStatus(UUID projectId, UUID userId, MemberStatus newStatus) {
        log.info("Updating member status: projectId={}, userId={}, newStatus={}", projectId, userId, newStatus);
        
        ProjectMember projectMember = projectMemberRepository.findMemberByProjectAndUser(projectId, userId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
        
        projectMember.setStatus(newStatus);
        ProjectMember updatedMember = projectMemberRepository.save(projectMember);
        
        return mapToProjectMemberResponseDto(updatedMember, new HashMap<>());
    }
    
    @Transactional
    public void removeMemberFromProject(UUID projectId, UUID userId) {
        log.info("Removing member from project: projectId={}, userId={}", projectId, userId);
        
        // Check if member exists
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new AppException(ProjectErrorCode.MEMBER_NOT_FOUND);
        }
        
        // Check if user is project manager (cannot remove project manager)
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        
        if (project.getManagerId().equals(userId)) {
            throw new AppException(ProjectErrorCode.PROJECT_MANAGER_CANNOT_BE_REMOVED);
        }
        
        // Check if member has active tasks (this would integrate with task service)
        // For now, just log a warning
        log.warn("Removing member with potential active tasks: projectId={}, userId={}", projectId, userId);
        
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }
    
    public boolean isProjectMember(UUID projectId, UUID userId) {
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, userId);
    }
    
    public boolean isProjectManager(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        return project.getManagerId().equals(userId);
    }
    
    public List<ProjectMemberResponseDto> getMembersByRole(UUID projectId, UUID roleId) {
        log.info("Getting project members by role: projectId={}, roleId={}", projectId, roleId);
        
        List<ProjectMember> members = projectMemberRepository.findByProjectIdAndRoleId(projectId, roleId);
        Map<UUID, UserDetailDto> userMap = getUserMapFromMembers(members);
        return members.stream()
                .map(member -> mapToProjectMemberResponseDto(member, userMap))
                .collect(Collectors.toList());
    }
    
    public List<UUID> getUserProjectIds(UUID userId) {
        log.info("Getting project IDs for user: {}", userId);
        
        List<ProjectMember> members = projectMemberRepository.findByUserId(userId);
        return members.stream()
                .map(member -> member.getProject().getId())
                .collect(Collectors.toList());
    }

    // Helper method to get user map from members
    private Map<UUID, UserDetailDto> getUserMapFromMembers(List<ProjectMember> members) {
        Set<UUID> userIds = members.stream()
                .flatMap(member -> Stream.of(
                        member.getUserId(),
                        member.getAssignedBy()
                ))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return new HashMap<>();
        }

        try {
            UserIdsDto userIdsDto = new UserIdsDto();
            userIdsDto.setIds(userIds);
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUsersByID(userIdsDto);
            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> usersData = (List<Map<String, Object>>) response.getBody().get("data");
                return usersData.stream()
                        .map(this::convertToUserDetailDto)
                        .collect(Collectors.toMap(UserDetailDto::getId, user -> user));
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.error("Error fetching users from User Service: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private ProjectMemberResponseDto mapToProjectMemberResponseDto(ProjectMember projectMember) {
        return mapToProjectMemberResponseDto(projectMember, new HashMap<>());
    }

    private ProjectMemberResponseDto mapToProjectMemberResponseDto(ProjectMember projectMember, Map<UUID, UserDetailDto> userMap) {
        ProjectMemberResponseDto dto = new ProjectMemberResponseDto();
        dto.setId(projectMember.getId());
        dto.setUserId(projectMember.getUserId());
        dto.setRoleId(projectMember.getRoleId());
        dto.setStatus(projectMember.getStatus());
        dto.setJoinedAt(projectMember.getJoinedAt());
        dto.setAssignedBy(projectMember.getAssignedBy());
        
        // Get project ID directly to avoid lazy loading issues
        UUID projectId = projectMember.getProject() != null ? projectMember.getProject().getId() : null;
        
        // Get role name from ProjectAllowedRole
        if (projectId != null) {
            try {
                List<ProjectAllowedRole> allowedRoles = projectAllowedRoleService.list(projectId);
                allowedRoles.stream()
                    .filter(role -> role.getRoleId().equals(projectMember.getRoleId()))
                    .findFirst()
                    .ifPresent(role -> dto.setRoleName(role.getRoleName()));
            } catch (Exception e) {
                log.warn("Could not get role name for roleId {}: {}", projectMember.getRoleId(), e.getMessage());
                dto.setRoleName("Unknown Role");
            }
        } else {
            dto.setRoleName("Unknown Role");
        }

        // Lấy thông tin user từ userMap hoặc UserService
        UserDetailDto user = userMap.get(projectMember.getUserId());
        if (user == null) {
            Optional<UserDetailDto> userOpt = getUserById(projectMember.getUserId());
            if (userOpt.isPresent()) {
                user = userOpt.get();
            }
        }
        if (user != null) {
            dto.setUserName(user.getFirstName() + " " + user.getLastName());
            dto.setUserEmail(user.getEmail());
            dto.setUserImage(user.getImage());
        } else {
            dto.setUserName("Unknown User");
            dto.setUserEmail("unknown@example.com");
            dto.setUserImage(null);
        }

        // Lấy thông tin assignedBy
        if (projectMember.getAssignedBy() != null) {
            UserDetailDto assignedByUser = userMap.get(projectMember.getAssignedBy());
            if (assignedByUser == null) {
                Optional<UserDetailDto> assignedByOpt = getUserById(projectMember.getAssignedBy());
                if (assignedByOpt.isPresent()) {
                    assignedByUser = assignedByOpt.get();
                }
            }
            if (assignedByUser != null) {
                dto.setAssignedByName(assignedByUser.getFirstName() + " " + assignedByUser.getLastName());
            } else {
                dto.setAssignedByName("Unknown User");
            }
        } else {
            dto.setAssignedByName(null);
        }
        return dto;
    }

}
