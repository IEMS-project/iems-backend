package com.iems.projectservice.controller;

import com.iems.projectservice.dto.response.LabelDto;
import com.iems.projectservice.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/labels")
@RequiredArgsConstructor
public class LabelController {
    private final LabelService service;

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<LabelDto>> getLabelsByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(service.getLabelsByProject(projectId));
    }

    @PostMapping
    public ResponseEntity<LabelDto> createLabel(@RequestBody Map<String, Object> payload) {
        UUID projectId = UUID.fromString(payload.get("projectId").toString());
        String name = payload.get("name").toString();
        String color = payload.get("color") != null ? payload.get("color").toString() : "#6b7280";
        return ResponseEntity.ok(service.createLabel(projectId, name, color));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LabelDto> updateLabel(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        String name = payload.get("name").toString();
        String color = payload.get("color").toString();
        return ResponseEntity.ok(service.updateLabel(id, name, color));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLabel(@PathVariable UUID id) {
        service.deleteLabel(id);
        return ResponseEntity.noContent().build();
    }
}
