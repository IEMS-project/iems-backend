package com.iems.projectservice.service;

import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.ProjectAllowedRole;
import com.iems.projectservice.repository.ProjectAllowedRoleRepository;
import com.iems.projectservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectAllowedRoleService {
    private final ProjectRepository projectRepository;
    private final ProjectAllowedRoleRepository allowedRoleRepository;

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
        allowedRoleRepository.deleteById(allowedRoleId);
    }
}


