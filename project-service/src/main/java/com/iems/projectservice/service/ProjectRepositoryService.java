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

    /**
     * Creates project repository data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param dto the dto parameter
     * @return the create repository result
     */
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

    /**
     * Retrieves project repository information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @return the matching result collection
     */
    public List<ProjectRepositoryDto> getRepositoriesByProjectId(UUID projectId) {
        log.info("Getting repositories for project: {}", projectId);
        
        List<GithubRepository> repositories = projectRepositoryRepository.findByProjectId(projectId);
        
        return repositories.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves project repository information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param id the id parameter
     * @return the get repository by id result
     */
    public ProjectRepositoryDto getRepositoryById(UUID id) {
        log.info("Getting repository by id: {}", id);
        
        GithubRepository repository = projectRepositoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        
        return mapToDto(repository);
    }

    /**
     * Updates project repository data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param id the id parameter
     * @param dto the dto parameter
     * @return the update repository result
     */
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

    /**
     * Deletes project repository data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param id the id parameter
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public void deleteRepository(UUID id) {
        log.info("Deleting repository: {}", id);
        
        if (!projectRepositoryRepository.existsById(id)) {
            throw new AppException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        
        projectRepositoryRepository.deleteById(id);
    }

    /**
     * Deletes project repository data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     */
    @Transactional
    public void deleteRepositoriesByProjectId(UUID projectId) {
        log.info("Deleting all repositories for project: {}", projectId);
        projectRepositoryRepository.deleteByProjectId(projectId);
    }

    /**
     * Maps project repository data to the target representation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param repository the repository parameter
     * @return the map to dto result
     */
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
