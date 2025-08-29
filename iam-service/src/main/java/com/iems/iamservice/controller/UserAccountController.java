package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.CreateUserDto;
import com.iems.iamservice.dto.request.UpdateUserDto;
import com.iems.iamservice.dto.response.UserResponseDto;
import com.iems.iamservice.mapper.IamMapper;
import com.iems.iamservice.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/iam/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "APIs for managing user accounts")
public class UserAccountController {

    private final UserAccountService userAccountService;

    @PostMapping
    @Operation(summary = "Create user", description = "Create a new user account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ApiResponseDto<UserResponseDto>> create(@RequestBody CreateUserDto dto) {
        var created = userAccountService.create(dto);
        var body = new ApiResponseDto<>("success", "created", IamMapper.toUserResponse(created));
        return ResponseEntity.created(URI.create("/api/iam/users/" + created.getId())).body(body);
    }

    @GetMapping
    @Operation(summary = "List users", description = "Get list of all user accounts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    })
    public ResponseEntity<ApiResponseDto<List<UserResponseDto>>> list() {
        var data = userAccountService.findAll().stream().map(IamMapper::toUserResponse).collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponseDto<>("success", "ok", data));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user", description = "Get a user account by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponseDto<UserResponseDto>> get(@PathVariable Long id) {
        var user = userAccountService.findById(id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "ok", IamMapper.toUserResponse(user)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update user", description = "Update details of an existing user account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponseDto<UserResponseDto>> update(@PathVariable Long id, @RequestBody UpdateUserDto dto) {
        var updated = userAccountService.update(id, dto);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "updated", IamMapper.toUserResponse(updated)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Delete a user account by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deleted"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        userAccountService.delete(id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "deleted", null));
    }
}


