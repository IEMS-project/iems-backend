package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateProjectRepositoryDto;
import com.iems.projectservice.dto.request.UpdateProjectRepositoryDto;
import com.iems.projectservice.dto.response.ProjectRepositoryDto;
import com.iems.projectservice.entity.GithubRepository;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectRepositoryService {

    private final ProjectRepositoryRepository projectRepositoryRepository;

    @Transactional
    public ProjectRepositoryDto createRepository(CreateProjectRepositoryDto dto) {
        log.info("Creating repository for project: {}", dto.getProjectId());
        
        GithubRepository repository = GithubRepository.builder()
                .projectId(dto.getProjectId())
                .name(dto.getName())
                .repoLink(dto.getRepoLink())
                .build();

        GithubRepository savedRepository = projectRepositoryRepository.save(repository);
        
        return mapToDto(savedRepository);
    }

    public List<ProjectRepositoryDto> getRepositoriesByProjectId(UUID projectId) {
        log.info("Getting repositories for project: {}", projectId);
        
        List<GithubRepository> repositories = projectRepositoryRepository.findByProjectId(projectId);
        
        return repositories.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ProjectRepositoryDto getRepositoryById(UUID id) {
        log.info("Getting repository by id: {}", id);
        
        GithubRepository repository = projectRepositoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        
        return mapToDto(repository);
    }

    @Transactional
    public ProjectRepositoryDto updateRepository(UUID id, UpdateProjectRepositoryDto dto) {
        log.info("Updating repository: {}", id);
        
        GithubRepository repository = projectRepositoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        
        repository.setName(dto.getName());
        repository.setRepoLink(dto.getRepoLink());
        
        GithubRepository updatedRepository = projectRepositoryRepository.save(repository);
        
        return mapToDto(updatedRepository);
    }

    @Transactional
    public void deleteRepository(UUID id) {
        log.info("Deleting repository: {}", id);
        
        if (!projectRepositoryRepository.existsById(id)) {
            throw new AppException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        
        projectRepositoryRepository.deleteById(id);
    }

    @Transactional
    public void deleteRepositoriesByProjectId(UUID projectId) {
        log.info("Deleting all repositories for project: {}", projectId);
        projectRepositoryRepository.deleteByProjectId(projectId);
    }

    private ProjectRepositoryDto mapToDto(GithubRepository repository) {
        return ProjectRepositoryDto.builder()
                .id(repository.getId())
                .projectId(repository.getProjectId())
                .name(repository.getName())
                .repoLink(repository.getRepoLink())
                .createdAt(repository.getCreatedAt())
                .updatedAt(repository.getUpdatedAt())
                .build();
    }
}
