package com.iems.projectservice.service;

import com.iems.projectservice.dto.response.LabelDto;
import com.iems.projectservice.entity.Label;
import com.iems.projectservice.repository.LabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LabelService {
    private final LabelRepository repository;

    /**
     * Retrieves label information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @return the matching result collection
     */
    public List<LabelDto> getLabelsByProject(UUID projectId) {
        return repository.findByProjectId(projectId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Creates label data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param name the name parameter
     * @param color the color parameter
     * @return the create label result
     */
    @Transactional
    public LabelDto createLabel(UUID projectId, String name, String color) {
        Label label = Label.builder()
                .projectId(projectId)
                .name(name)
                .color(color)
                .build();
        return convertToDto(repository.save(label));
    }

    /**
     * Updates label data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param id the id parameter
     * @param name the name parameter
     * @param color the color parameter
     * @return the update label result
     */
    @Transactional
    public LabelDto updateLabel(UUID id, String name, String color) {
        Label label = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Label not found"));
        label.setName(name);
        label.setColor(color);
        return convertToDto(repository.save(label));
    }

    /**
     * Deletes label data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param id the id parameter
     */
    @Transactional
    public void deleteLabel(UUID id) {
        repository.deleteById(id);
    }

    /**
     * Converts label data to the target representation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param label the label parameter
     * @return the convert to dto result
     */
    public LabelDto convertToDto(Label label) {
        return LabelDto.builder()
                .id(label.getId())
                .name(label.getName())
                .color(label.getColor())
                .build();
    }
}
