package com.iems.projectservice.service;

import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.ProjectAllowedRole;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectAllowedRoleRepository;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectAllowedRoleService {
    private final ProjectRepository projectRepository;
    private final ProjectAllowedRoleRepository allowedRoleRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public List<ProjectAllowedRole> list(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        return allowedRoleRepository.findByProject(project);
    }

    @Transactional
    public ProjectAllowedRole add(UUID projectId, String roleName) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        
        // Case-insensitive duplicate check
        if (allowedRoleRepository.findByProjectAndRoleNameIgnoreCase(project, roleName).isPresent()) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }
        
        ProjectAllowedRole role = new ProjectAllowedRole();
        role.setProject(project);
        role.setRoleName(roleName);
        return allowedRoleRepository.save(role);
    }

    @Transactional
    public void delete(UUID projectId, UUID allowedRoleId) {
        log.info("Attempting to delete role with id: {} from project: {}", allowedRoleId, projectId);
        
        // Get the allowed role
        ProjectAllowedRole allowedRole = allowedRoleRepository.findById(allowedRoleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        
        log.info("Found role: {}", allowedRole.getRoleName());
        
        // Check if any member is using this role (roleId in project_members references allowedRole.id)
        List<ProjectMember> members = projectMemberRepository.findByProjectIdAndRoleId(projectId, allowedRole.getId());
        log.info("Found {} members using this role", members.size());
        
        if (!members.isEmpty()) {
            log.warn("Cannot delete role {} - it is assigned to {} members", allowedRole.getRoleName(), members.size());
            throw new AppException(ProjectErrorCode.ROLE_ALREADY_ASSIGNED);
        }
        
        log.info("Deleting role {}", allowedRole.getRoleName());
        allowedRoleRepository.deleteById(allowedRoleId);
    }
}


