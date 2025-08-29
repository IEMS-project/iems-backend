package com.iems.taskservice.controller;

import com.iems.taskservice.dto.ApiResponseDto;
import com.iems.taskservice.dto.CreateTaskDto;
import com.iems.taskservice.dto.TaskResponseDto;
import com.iems.taskservice.dto.UpdateTaskDto;
import com.iems.taskservice.entity.TaskStatusHistory;
import com.iems.taskservice.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@CrossOrigin(origins = "*")
@Tag(name = "Task API", description = "Manage tasks")
public class TaskController {
    @Autowired
    private TaskService taskService;

    @PostMapping
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Failed to create task")
    })
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> createTask(
            @Valid @RequestBody CreateTaskDto createDto, 
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            TaskResponseDto responseDto = taskService.createTask(createDto, userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task created successfully", responseDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to create task", null));
        }
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign task", description = "Assign task to a new assignee")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task assigned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Failed to assign task")
    })
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> assignTask(
            @PathVariable UUID id, 
            @RequestParam UUID newAssigneeId,
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            TaskResponseDto updatedDto = taskService.assignTask(id, newAssigneeId, userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task assigned successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to assign task", null));
        }
    }

    @GetMapping("/my-tasks")
    @Operation(summary = "Get my tasks", description = "Get tasks assigned to the current user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to retrieve tasks")
    })
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getMyTasks(@RequestHeader("X-User-Id") UUID userId) {
        try {
            List<TaskResponseDto> tasks = taskService.getMyTasks(userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "My tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve my tasks", null));
        }
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update task status", description = "Update the status of a task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Failed to update task status")
    })
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> updateTaskStatus(
            @PathVariable UUID id, 
            @RequestParam String newStatus,
            @RequestParam(required = false) String comment,
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            TaskResponseDto updatedDto = taskService.updateTaskStatus(id, newStatus, comment, userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task status updated successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to update task status", null));
        }
    }

    @PatchMapping("/{id}/priority-date")
    @Operation(summary = "Update task priority & dates", description = "Set priority and dates for a task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Failed to update task")
    })
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> updateTaskPriorityDate(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateTaskDto updateDto, 
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            TaskResponseDto updatedDto = taskService.updateTaskPriorityAndDates(id, updateDto, userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task priority and dates updated successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to update task priority and dates", null));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID", description = "Retrieve a single task by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Task not found"),
            @ApiResponse(responseCode = "500", description = "Failed to retrieve task")
    })
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> getTaskById(@PathVariable UUID id) {
        try {
            return taskService.getTaskById(id)
                    .map(task -> ResponseEntity.ok(new ApiResponseDto<>("success", "Task retrieved successfully", task)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDto<>("error", "Task not found", null)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve task", null));
        }
    }

    @GetMapping
    @Operation(summary = "Get all tasks", description = "Retrieve all tasks")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to retrieve tasks")
    })
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getAllTasks() {
        try {
            List<TaskResponseDto> tasks = taskService.getAllTasks();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "All tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve tasks", null));
        }
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get tasks by project", description = "Retrieve tasks that belong to a specific project")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to retrieve tasks")
    })
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getTasksByProject(@PathVariable UUID projectId) {
        try {
            List<TaskResponseDto> tasks = taskService.getTasksByProject(projectId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve project tasks", null));
        }
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get task history", description = "Retrieve status change history for a task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task history retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to retrieve task history")
    })
    public ResponseEntity<ApiResponseDto<List<TaskStatusHistory>>> getTaskStatusHistory(@PathVariable UUID id) {
        try {
            List<TaskStatusHistory> history = taskService.getTaskStatusHistory(id);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task history retrieved successfully", history));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve task history", null));
        }
    }

    @GetMapping("/my-tasks/filter")
    @Operation(summary = "Get my tasks with filter", description = "Filter my tasks by status or priority")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Filtered tasks retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to retrieve tasks")
    })
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getMyTasksWithFilter(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        try {
            List<TaskResponseDto> tasks;
            if (status != null && priority != null) {
                tasks = taskService.getMyTasks(userId);
            } else if (status != null) {
                tasks = taskService.getMyTasks(userId);
            } else if (priority != null) {
                tasks = taskService.getMyTasks(userId);
            } else {
                tasks = taskService.getMyTasks(userId);
            }
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Filtered tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve filtered tasks", null));
        }
    }
}