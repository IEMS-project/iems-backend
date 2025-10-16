package com.iems.projectservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectProgressDto {
    private int totalTasks;
    private int completedTasks;
    private int inProgressTasks;
    private int pendingTasks;
    private int overdueTasks;
    private double completionPercentage;
}

