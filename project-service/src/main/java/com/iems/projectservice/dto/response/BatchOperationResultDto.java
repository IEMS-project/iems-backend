package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResultDto<T> {
    private int requestedCount;
    private int successCount;
    private int skippedCount;
    private List<T> items;
}
