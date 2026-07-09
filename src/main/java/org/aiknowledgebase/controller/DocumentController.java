package org.aiknowledgebase.controller;

import org.aiknowledgebase.dto.DocumentUploadResponse;
import org.aiknowledgebase.entity.DocumentInfo;
import org.aiknowledgebase.entity.TextChunk;
import org.aiknowledgebase.service.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传文档
     */
    @PostMapping("/upload")
    public DocumentUploadResponse uploadDocument(@RequestParam Long knowledgeBaseId,
                                                 @RequestParam MultipartFile file) {
        return documentService.uploadDocument(knowledgeBaseId, file);
    }

    /**
     * 查询某个知识库下的所有文档
     */
    @GetMapping
    public List<DocumentInfo> listDocuments(@RequestParam Long knowledgeBaseId) {
        return documentService.listDocuments(knowledgeBaseId);
    }

    /**
     * 查询某个文档的所有文本切片
     */
    @GetMapping("/{documentId}/chunks")
    public List<TextChunk> listChunks(@PathVariable Long documentId) {
        return documentService.listChunks(documentId);
    }
}