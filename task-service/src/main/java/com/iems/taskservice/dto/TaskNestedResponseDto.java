package com.iems.taskservice.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TaskNestedResponseDto {
    private UUID id;
    private ProjectInfo project;
    private String title;
    private String description;
    private UserInfo assignedTo;
    private UserInfo createdBy;
    private UserInfo updatedBy;
    private String status;
    private String priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class ProjectInfo {
        private UUID id;
        private String name;
    }

    @Data
    public static class UserInfo {
        private UUID id;
        private String name;
        private String image;
    }
}


