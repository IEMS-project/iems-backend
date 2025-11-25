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
    public ProjectAllowedRole add(UUID projectId, UUID roleId, String roleName) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        return allowedRoleRepository.findByProjectAndRoleId(project, roleId)
                .orElseGet(() -> allowedRoleRepository.save(new ProjectAllowedRole(null, project, roleId, roleName)));
    }

    @Transactional
    public void delete(UUID projectId, UUID allowedRoleId) {
        log.info("Attempting to delete role with id: {} from project: {}", allowedRoleId, projectId);
        
        // Get the allowed role to check roleId
        ProjectAllowedRole allowedRole = allowedRoleRepository.findById(allowedRoleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        
        log.info("Found role: {} (roleId: {})", allowedRole.getRoleName(), allowedRole.getRoleId());
        
        // Check if any member is using this role
        List<ProjectMember> members = projectMemberRepository.findByProjectIdAndRoleId(projectId, allowedRole.getRoleId());
        log.info("Found {} members using this role", members.size());
        
        if (!members.isEmpty()) {
            log.warn("Cannot delete role {} - it is assigned to {} members", allowedRole.getRoleName(), members.size());
            throw new AppException(ProjectErrorCode.ROLE_ALREADY_ASSIGNED);
        }
        
        log.info("Deleting role {}", allowedRole.getRoleName());
        allowedRoleRepository.deleteById(allowedRoleId);
    }
}


