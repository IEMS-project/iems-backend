package com.iems.projectservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleFileResponse {
    private String id;
    private String fileName;
    private String url;
    private String type;
}
