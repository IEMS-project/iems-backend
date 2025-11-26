package com.iems.taskservice.service;

import com.iems.taskservice.dto.*;
import com.iems.taskservice.entity.Task;
import com.iems.taskservice.entity.TaskComment;
import com.iems.taskservice.entity.TaskStatusHistory;
import com.iems.taskservice.entity.enums.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ITaskService {


    TaskResponseDto createTask(CreateTaskDto createDto);

    TaskResponseDto assignTask(UUID taskId, UUID newAssigneeId);

    List<MyTaskResponseDto> getMyTasks();

    TaskResponseDto updateTaskStatus(UUID taskId, String newStatusStr, String comment);

    List<TaskBulkUpdateItemDto> bulkUpdateStatus(List<UUID> taskIds, String newStatusStr);

    TaskComment addComment(UUID taskId, String content);

    List<TaskComment> getComments(UUID taskId);

    TaskResponseDto updateTaskPriorityAndDates(UUID taskId, UpdateTaskDto updateDto);

    Optional<TaskResponseDto> getTaskById(UUID id);

    Optional<TaskNestedResponseDto> getTaskByIdNested(UUID id);

    List<TaskResponseDto> getAllTasks();

    List<TaskResponseDto> getTasksByProject(UUID projectId);

    List<TaskNestedResponseDto> getTasksByProjectNested(UUID projectId);

    List<TaskResponseDto> getSubtasks(UUID parentTaskId);

    TaskUpdateResultDto updateTask(UUID taskId, UpdateTaskDto updateDto);

    List<TaskResponseDto> getActiveTasksByProject(UUID projectId);

    List<TaskStatusHistory> getTaskStatusHistory(UUID taskId);

    // Helper methods
    void createStatusHistory(Task task, TaskStatus newStatus, UUID updatedBy, String _comment);

    void createStatusHistoryWithOldNew(UUID taskId, TaskStatus oldStatus, TaskStatus newStatus, UUID updatedBy);

    boolean isValidStatusTransition(TaskStatus currentStatus, TaskStatus newStatus);

    List<ProjectProgressDto> getProjectsProgress(List<UUID> projectIds);
}
