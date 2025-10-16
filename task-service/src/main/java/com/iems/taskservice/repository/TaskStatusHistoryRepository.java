package com.iems.taskservice.repository;

import com.iems.taskservice.entity.TaskStatusHistory;
import com.iems.taskservice.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, UUID> {
    List<TaskStatusHistory> findByTaskIdOrderByUpdatedAtDesc(UUID taskId);
    
    List<TaskStatusHistory> findByTaskIdAndNewStatus(UUID taskId, TaskStatus newStatus);
}