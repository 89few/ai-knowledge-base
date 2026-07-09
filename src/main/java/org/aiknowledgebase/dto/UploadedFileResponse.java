package org.aiknowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadedFileResponse {

    private Long fileId;

    private String filename;

    private String fileType;

    private Integer chunkCount;

    private String status;

    private String message;
}