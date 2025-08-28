package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponse;
import com.iems.iamservice.dto.request.CreateRoleDto;
import com.iems.iamservice.dto.request.UpdateRoleDto;
import com.iems.iamservice.dto.response.RoleResponseDto;
import com.iems.iamservice.mapper.IamMapper;
import com.iems.iamservice.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/iam/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponseDto>> create(@RequestBody CreateRoleDto dto) {
        var created = roleService.create(dto);
        var body = new ApiResponse<>("success", "created", IamMapper.toRoleResponse(created));
        return ResponseEntity.created(URI.create("/api/iam/roles/" + created.getId())).body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponseDto>>> list() {
        var data = roleService.findAll().stream().map(IamMapper::toRoleResponse).collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponse<>("success", "ok", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponseDto>> get(@PathVariable Long id) {
        var role = roleService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>("success", "ok", IamMapper.toRoleResponse(role)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponseDto>> update(@PathVariable Long id, @RequestBody UpdateRoleDto dto) {
        var updated = roleService.update(id, dto);
        return ResponseEntity.ok(new ApiResponse<>("success", "updated", IamMapper.toRoleResponse(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>("success", "deleted", null));
    }
}


