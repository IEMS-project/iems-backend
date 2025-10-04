package com.iems.documentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FolderContentsResponse {
    private List<FolderResponse> folders;
    private List<FileResponse> files;
}
