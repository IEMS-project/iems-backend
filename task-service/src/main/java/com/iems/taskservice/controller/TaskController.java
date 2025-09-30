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
@Tag(name = "Task API", description = "Manage tasks")
public class TaskController {
    @Autowired
    private TaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> createTask(
            @Valid @RequestBody CreateTaskDto createDto) {
        try {
            TaskResponseDto responseDto = taskService.createTask(createDto);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task created successfully", responseDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to create task", null));
        }
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign task", description = "Assign task to a new assignee")
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> assignTask(
            @PathVariable UUID id, 
            @RequestParam UUID newAssigneeId) {
        try {
            TaskResponseDto updatedDto = taskService.assignTask(id, newAssigneeId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task assigned successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to assign task", null));
        }
    }

    @GetMapping("/my-tasks")
    @Operation(summary = "Get my tasks", description = "Get tasks assigned to the current user")
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getMyTasks() {
        try {
            List<TaskResponseDto> tasks = taskService.getMyTasks();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "My tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve my tasks", null));
        }
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update task status", description = "Update the status of a task")
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> updateTaskStatus(
            @PathVariable UUID id, 
            @RequestParam String newStatus,
            @RequestParam(required = false) String comment){
        try {
            TaskResponseDto updatedDto = taskService.updateTaskStatus(id, newStatus, comment);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task status updated successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to update task status", null));
        }
    }

    @PatchMapping("/{id}/priority-date")
    @Operation(summary = "Update task priority & dates", description = "Set priority and dates for a task")
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> updateTaskPriorityDate(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateTaskDto updateDto) {
        try {
            TaskResponseDto updatedDto = taskService.updateTaskPriorityAndDates(id, updateDto);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task priority and dates updated successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to update task priority and dates", null));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID", description = "Retrieve a single task by its ID")
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
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getMyTasksWithFilter(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        try {
            List<TaskResponseDto> tasks;
            if (status != null && priority != null) {
                tasks = taskService.getMyTasks();
            } else if (status != null) {
                tasks = taskService.getMyTasks();
            } else if (priority != null) {
                tasks = taskService.getMyTasks();
            } else {
                tasks = taskService.getMyTasks();
            }
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Filtered tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve filtered tasks", null));
        }
    }
}