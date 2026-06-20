package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateProjectRepositoryDto;
import com.iems.projectservice.dto.request.UpdateProjectRepositoryDto;
import com.iems.projectservice.dto.response.ProjectRepositoryDto;
import com.iems.projectservice.entity.GithubRepository;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.repository.ProjectRepositoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectRepositoryServiceTest {

    @Mock
    private ProjectRepositoryRepository projectRepositoryRepository;

    private ProjectRepositoryService service;

    @BeforeEach
    void setUp() {
        service = new ProjectRepositoryService(projectRepositoryRepository);
    }

    @Test
    void createRepositoryShouldPersistAndMap() {
        UUID projectId = UUID.randomUUID();
        GithubRepository saved = GithubRepository.builder().id(UUID.randomUUID()).projectId(projectId).name("repo").repoLink("https://github.com/org/repo").build();
        when(projectRepositoryRepository.save(any(GithubRepository.class))).thenReturn(saved);

        CreateProjectRepositoryDto dto = new CreateProjectRepositoryDto();
        dto.setProjectId(projectId);
        dto.setName("repo");
        dto.setRepoLink("https://github.com/org/repo");

        ProjectRepositoryDto result = service.createRepository(dto);

        assertEquals(projectId, result.getProjectId());
        assertEquals("repo", result.getName());
    }

    @Test
    void getRepositoryByIdShouldThrowWhenMissing() {
        when(projectRepositoryRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> service.getRepositoryById(UUID.randomUUID()));
    }

    @Test
    void updateRepositoryShouldSaveChanges() {
        UUID id = UUID.randomUUID();
        GithubRepository repository = GithubRepository.builder().id(id).name("old").repoLink("old").build();
        when(projectRepositoryRepository.findById(id)).thenReturn(Optional.of(repository));
        when(projectRepositoryRepository.save(repository)).thenReturn(repository);

        UpdateProjectRepositoryDto dto = new UpdateProjectRepositoryDto();
        dto.setName("new");
        dto.setRepoLink("https://example.com/new");

        ProjectRepositoryDto result = service.updateRepository(id, dto);

        assertEquals("new", result.getName());
        verify(projectRepositoryRepository).save(repository);
    }

    @Test
    void deleteRepositoryShouldDeleteWhenExists() {
        UUID id = UUID.randomUUID();
        when(projectRepositoryRepository.existsById(id)).thenReturn(true);

        service.deleteRepository(id);

        verify(projectRepositoryRepository).deleteById(id);
    }

    @Test
    void deleteRepositoriesByProjectIdShouldDelegate() {
        UUID projectId = UUID.randomUUID();

        service.deleteRepositoriesByProjectId(projectId);

        verify(projectRepositoryRepository).deleteByProjectId(projectId);
    }
}