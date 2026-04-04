package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueImportResultDto {
    private int totalRows;
    private int insertedCount;
    private int updatedCount;
    private int unchangedCount;
    private String message;
}
