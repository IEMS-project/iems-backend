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

    public List<LabelDto> getLabelsByProject(UUID projectId) {
        return repository.findByProjectId(projectId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public LabelDto createLabel(UUID projectId, String name, String color) {
        Label label = Label.builder()
                .projectId(projectId)
                .name(name)
                .color(color)
                .build();
        return convertToDto(repository.save(label));
    }

    @Transactional
    public LabelDto updateLabel(UUID id, String name, String color) {
        Label label = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Label not found"));
        label.setName(name);
        label.setColor(color);
        return convertToDto(repository.save(label));
    }

    @Transactional
    public void deleteLabel(UUID id) {
        repository.deleteById(id);
    }

    public LabelDto convertToDto(Label label) {
        return LabelDto.builder()
                .id(label.getId())
                .name(label.getName())
                .color(label.getColor())
                .build();
    }
}
