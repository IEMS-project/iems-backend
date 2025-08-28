package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponse;
import com.iems.iamservice.dto.request.CreatePermissionDto;
import com.iems.iamservice.dto.request.UpdatePermissionDto;
import com.iems.iamservice.dto.response.PermissionResponseDto;
import com.iems.iamservice.mapper.IamMapper;
import com.iems.iamservice.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/iam/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponseDto>> create(@RequestBody CreatePermissionDto dto) {
        var created = permissionService.create(dto);
        var body = new ApiResponse<>("success", "created", IamMapper.toPermissionResponse(created));
        return ResponseEntity.created(URI.create("/api/iam/permissions/" + created.getId())).body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponseDto>>> list() {
        var data = permissionService.findAll().stream().map(IamMapper::toPermissionResponse).collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponse<>("success", "ok", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponseDto>> get(@PathVariable Long id) {
        var p = permissionService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>("success", "ok", IamMapper.toPermissionResponse(p)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponseDto>> update(@PathVariable Long id, @RequestBody UpdatePermissionDto dto) {
        var updated = permissionService.update(id, dto);
        return ResponseEntity.ok(new ApiResponse<>("success", "updated", IamMapper.toPermissionResponse(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>("success", "deleted", null));
    }
}


