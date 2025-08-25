package com.iems.taskservice.repository;

import com.iems.taskservice.entity.Task;
import com.iems.taskservice.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByAssignedTo(UUID assignedTo);
    
    List<Task> findByProjectId(UUID projectId);
    
    List<Task> findByProjectIdAndStatus(UUID projectId, TaskStatus status);
    
    List<Task> findByAssignedToAndStatus(UUID assignedTo, TaskStatus status);
    
    List<Task> findByAssignedToAndPriority(UUID assignedTo, String priority);
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo = :userId AND t.dueDate <= :dueDate")
    List<Task> findTasksDueByDate(@Param("userId") UUID userId, @Param("dueDate") LocalDate dueDate);
    
    @Query("SELECT t FROM Task t WHERE t.assignedTo = :userId AND t.dueDate < :today")
    List<Task> findOverdueTasks(@Param("userId") UUID userId, @Param("today") LocalDate today);
    
    @Query("SELECT t FROM Task t WHERE t.projectId = :projectId AND t.status != :completedStatus")
    List<Task> findActiveTasksByProject(@Param("projectId") UUID projectId, @Param("completedStatus") TaskStatus completedStatus);
}