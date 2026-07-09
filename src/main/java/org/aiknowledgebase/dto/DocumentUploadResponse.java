package org.aiknowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentUploadResponse {

    private Long documentId;

    private String fileName;

    private Integer chunkCount;

    private String status;
}