package com.iems.projectservice.controller;

import com.iems.projectservice.dto.*;
import com.iems.projectservice.dto.request.ProjectIdsDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.ProjectProgressDto;
import com.iems.projectservice.entity.TaskStatusHistory;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.service.ITaskService;
import com.iems.projectservice.service.Impl.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@Tag(name = "Task API", description = "Manage tasks")
public class TaskController {
    @Autowired
    private ITaskService taskService;

    @Autowired
    private TaskService taskServiceImpl;



    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create task with attachments")
    public ResponseEntity<ApiResponseDto<TaskNestedResponseDto>> createTask(
            @Valid @RequestPart("task") CreateTaskDto createDto,
            @RequestPart(value = "files", required = false) MultipartFile[] files
    ) {
        try {
            TaskResponseDto responseDto;
            if (files != null && files.length > 0) {
                responseDto = taskServiceImpl.createTask(createDto, files);
            } else {
                responseDto = taskService.createTask(createDto);
            }

            return taskService.getTaskByIdNested(responseDto.getId())
                    .map(nested -> ResponseEntity.ok(
                            new ApiResponseDto<>("success", "Task created successfully", nested)))
                    .orElseGet(() -> ResponseEntity.ok(
                            new ApiResponseDto<>("success", "Task created successfully", null)));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponseDto<>("error", "Failed to create task", null));
        }
    }


    @PatchMapping(value = "/{id}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Update task with attachments", description = "Update task fields: title, description, assignedTo, priority, dates, status with optional file attachments")
    public ResponseEntity<ApiResponseDto<TaskUpdateResultDto>> updateTask(
            @PathVariable UUID id,
            @Valid @RequestPart(value = "task", required = false) UpdateTaskDto updateDtoMultipart,
            @Valid @RequestBody(required = false) UpdateTaskDto updateDtoJson,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        try {
            // Use multipart DTO if available, otherwise use JSON DTO
            UpdateTaskDto updateDto = updateDtoMultipart != null ? updateDtoMultipart : updateDtoJson;
            
            if (updateDto == null) {
                return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Task data is required", null));
            }
            
            TaskUpdateResultDto updated;
            if (files != null && files.length > 0) {
                updated = taskServiceImpl.updateTask(id, updateDto, files);
            } else {
                updated = taskService.updateTask(id, updateDto);
            }
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task updated successfully", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to update task", null));
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
    public ResponseEntity<ApiResponseDto<List<MyTaskResponseDto>>> getMyTasks() {
        try {
            List<MyTaskResponseDto> tasks = taskService.getMyTasks();
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

    @GetMapping("/{id}/subtasks")
    @Operation(summary = "Get subtasks", description = "Retrieve all subtasks of a task")
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getSubtasks(@PathVariable UUID id) {
        try {
            List<TaskResponseDto> tasks = taskService.getSubtasks(id);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Subtasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve subtasks", null));
        }
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get tasks by project", description = "Retrieve tasks that belong to a specific project")
    public ResponseEntity<ApiListResponseDto<List<TaskNestedResponseDto>>> getTasksByProject(@PathVariable UUID projectId) {
        try {
            List<TaskNestedResponseDto> tasks = taskService.getTasksByProjectNested(projectId);
            return ResponseEntity.ok(new ApiListResponseDto<>("success", "Project tasks retrieved successfully", tasks.size(), tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiListResponseDto<>("error", "Failed to retrieve project tasks", 0, null));
        }
    }

    @PostMapping("/status-bulk")
    @Operation(summary = "Bulk update status", description = "Update status for multiple tasks at once")
    public ResponseEntity<ApiListResponseDto<List<TaskBulkUpdateItemDto>>> bulkUpdateStatus(
            @RequestParam String newStatus,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
            List<UUID> taskIds = ids.stream().map(UUID::fromString).toList();
            List<TaskBulkUpdateItemDto> updated = taskService.bulkUpdateStatus(taskIds, newStatus);
            return ResponseEntity.ok(new ApiListResponseDto<>("success", "Tasks updated successfully", updated.size(), updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiListResponseDto<>("error", e.getMessage(), 0, null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiListResponseDto<>("error", "Failed to bulk update status", 0, null));
        }
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add task comment")
    public ResponseEntity<ApiResponseDto<TaskCommentDto>> addComment(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String content = (String) body.get("content");
            String parentIdStr = body.get("parentCommentId") != null ? body.get("parentCommentId").toString() : null;
            UUID parentCommentId = parentIdStr != null && !parentIdStr.isEmpty() ? UUID.fromString(parentIdStr) : null;
            TaskCommentDto c = taskService.addComment(id, content, parentCommentId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Comment added", c));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to add comment", null));
        }
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Get task comments")
    public ResponseEntity<ApiListResponseDto<List<TaskCommentDto>>> getComments(@PathVariable UUID id) {
        try {
            List<TaskCommentDto> list = taskService.getComments(id);
            return ResponseEntity.ok(new ApiListResponseDto<>("success", "Comments retrieved", list.size(), list));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiListResponseDto<>("error", "Failed to get comments", 0, null));
        }
    }

    @PutMapping("/comments/{commentId}")
    @Operation(summary = "Update task comment")
    public ResponseEntity<ApiResponseDto<TaskCommentDto>> updateComment(
            @PathVariable UUID commentId,
            @RequestBody Map<String, String> body) {
        try {
            String content = body.get("content");
            TaskCommentDto c = taskService.updateComment(commentId, content);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Comment updated", c));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to update comment", null));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Delete task comment")
    public ResponseEntity<ApiResponseDto<Void>> deleteComment(@PathVariable UUID commentId) {
        try {
            taskService.deleteComment(commentId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Comment deleted", null));
        } catch (AppException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
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

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete task", description = "Delete a task and all its related data")
    public ResponseEntity<ApiResponseDto<Void>> deleteTask(@PathVariable UUID id) {
        try {
            taskService.deleteTask(id);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task deleted successfully", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to delete task", null));
        }
    }

    @GetMapping("/my-tasks/filter")
    @Operation(summary = "Get my tasks with filter", description = "Filter my tasks by status or priority")
    public ResponseEntity<ApiResponseDto<List<MyTaskResponseDto>>> getMyTasksWithFilter(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        try {
            List<MyTaskResponseDto> tasks;
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



    @PostMapping("/project-completions")
    @Operation(summary = "Calculate project completion percentages", description = "Calculate completion percentage for each project based on phases and tasks")
    public ResponseEntity<ApiResponseDto<List<ProjectProgressDto>>> calculateProjectCompletions(
            @RequestBody ProjectIdsDto projectIdsDto) {
        try {
            List<UUID> projectIds = new ArrayList<>(projectIdsDto.getIds());
            List<ProjectProgressDto> completions = taskService.getProjectsProgress(projectIds);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project completions calculated successfully", completions));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to calculate project completions", null));
        }
    }

    @DeleteMapping("/{taskId}/attachments/{attachmentId}")
    @Operation(summary = "Delete task attachment", description = "Delete a specific attachment from a task")
    public ResponseEntity<ApiResponseDto<Void>> deleteAttachment(
            @PathVariable UUID taskId,
            @PathVariable UUID attachmentId) {
        try {
            taskServiceImpl.deleteAttachment(taskId, attachmentId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Attachment deleted successfully", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to delete attachment", null));
        }
    }
}
