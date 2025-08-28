package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponse;
import com.iems.iamservice.dto.request.CreateUserDto;
import com.iems.iamservice.dto.request.UpdateUserDto;
import com.iems.iamservice.dto.response.UserResponseDto;
import com.iems.iamservice.mapper.IamMapper;
import com.iems.iamservice.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/iam/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userAccountService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponseDto>> create(@RequestBody CreateUserDto dto) {
        var created = userAccountService.create(dto);
        var body = new ApiResponse<>("success", "created", IamMapper.toUserResponse(created));
        return ResponseEntity.created(URI.create("/api/iam/users/" + created.getId())).body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponseDto>>> list() {
        var data = userAccountService.findAll().stream().map(IamMapper::toUserResponse).collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponse<>("success", "ok", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDto>> get(@PathVariable Long id) {
        var user = userAccountService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>("success", "ok", IamMapper.toUserResponse(user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDto>> update(@PathVariable Long id, @RequestBody UpdateUserDto dto) {
        var updated = userAccountService.update(id, dto);
        return ResponseEntity.ok(new ApiResponse<>("success", "updated", IamMapper.toUserResponse(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        userAccountService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>("success", "deleted", null));
    }
}


