package com.iems.projectservice.repository;

import com.iems.projectservice.entity.Label;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabelRepository extends JpaRepository<Label, UUID> {
    List<Label> findByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
