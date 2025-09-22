package com.iems.projectservice.service;

import com.iems.projectservice.dto.response.ProjectMemberResponseDto;
import com.iems.projectservice.dto.external.UserInfoDto;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.entity.enums.ProjectRole;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberService {
    
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserService userService;
    
    @Transactional
    public ProjectMemberResponseDto addMemberToProject(UUID projectId, UUID userId, ProjectRole role, UUID assignedBy) {
        log.info("Adding member to project: projectId={}, userId={}, role={}", projectId, userId, role);
        
        // Validate project exists
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        // Check if user is already a member
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new RuntimeException("User is already a member of this project");
        }
        
        // Create project member
        ProjectMember projectMember = new ProjectMember();
        projectMember.setProject(project);
        projectMember.setUserId(userId);
        projectMember.setRole(role);
        projectMember.setJoinedAt(LocalDateTime.now());
        projectMember.setAssignedBy(assignedBy);
        
        ProjectMember savedMember = projectMemberRepository.save(projectMember);
        
        return mapToProjectMemberResponseDto(savedMember);
    }
    
    public List<ProjectMemberResponseDto> getProjectMembers(UUID projectId) {
        log.info("Getting project members: projectId={}", projectId);
        
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        return members.stream()
                .map(this::mapToProjectMemberResponseDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public ProjectMemberResponseDto updateMemberRole(UUID projectId, UUID userId, ProjectRole newRole, UUID updatedBy) {
        log.info("Updating member role: projectId={}, userId={}, newRole={}", projectId, userId, newRole);
        
        ProjectMember projectMember = projectMemberRepository.findMemberByProjectAndUser(projectId, userId)
                .orElseThrow(() -> new RuntimeException("Project member not found"));
        
        projectMember.setRole(newRole);
        ProjectMember updatedMember = projectMemberRepository.save(projectMember);
        
        return mapToProjectMemberResponseDto(updatedMember);
    }
    
    @Transactional
    public void removeMemberFromProject(UUID projectId, UUID userId, UUID removedBy) {
        log.info("Removing member from project: projectId={}, userId={}", projectId, userId);
        
        // Check if member exists
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new RuntimeException("Project member not found");
        }
        
        // Check if user is project manager (cannot remove project manager)
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (project.getManagerId().equals(userId)) {
            throw new RuntimeException("Cannot remove project manager from project");
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
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return project.getManagerId().equals(userId);
    }
    
    public List<ProjectMemberResponseDto> getMembersByRole(UUID projectId, ProjectRole role) {
        log.info("Getting project members by role: projectId={}, role={}", projectId, role);
        
        List<ProjectMember> members = projectMemberRepository.findByProjectIdAndRole(projectId, role);
        return members.stream()
                .map(this::mapToProjectMemberResponseDto)
                .collect(Collectors.toList());
    }
    
    public List<UUID> getUserProjectIds(UUID userId) {
        log.info("Getting project IDs for user: {}", userId);
        
        List<ProjectMember> members = projectMemberRepository.findByUserId(userId);
        return members.stream()
                .map(member -> member.getProject().getId())
                .collect(Collectors.toList());
    }
    
    private ProjectMemberResponseDto mapToProjectMemberResponseDto(ProjectMember projectMember) {
        ProjectMemberResponseDto dto = new ProjectMemberResponseDto();
        dto.setId(projectMember.getId());
        dto.setUserId(projectMember.getUserId());
        dto.setRole(projectMember.getRole());
        dto.setJoinedAt(projectMember.getJoinedAt());
        dto.setAssignedBy(projectMember.getAssignedBy());
        
        // Get user information from user service
        try {
            UserInfoDto userInfo = userService.getUserById(projectMember.getUserId());
            dto.setUserName(userInfo.getFirstName() + " " + userInfo.getLastName());
            dto.setUserEmail(userInfo.getEmail());
        } catch (Exception e) {
            log.warn("Could not fetch user info for userId: {}", projectMember.getUserId());
            dto.setUserName("Unknown User");
            dto.setUserEmail("unknown@example.com");
        }
        
        // Get assigned by user information
        try {
            UserInfoDto assignedByUser = userService.getUserById(projectMember.getAssignedBy());
            dto.setAssignedByName(assignedByUser.getFirstName() + " " + assignedByUser.getLastName());
        } catch (Exception e) {
            log.warn("Could not fetch assigned by user info for userId: {}", projectMember.getAssignedBy());
            dto.setAssignedByName("Unknown User");
        }
        
        return dto;
    }
}
