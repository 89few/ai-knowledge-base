package org.aiknowledgebase.service;

import org.aiknowledgebase.dto.DocumentUploadResponse;
import org.aiknowledgebase.entity.DocumentInfo;
import org.aiknowledgebase.entity.KnowledgeBase;
import org.aiknowledgebase.entity.TextChunk;
import org.aiknowledgebase.repository.DocumentInfoRepository;
import org.aiknowledgebase.repository.KnowledgeBaseRepository;
import org.aiknowledgebase.repository.TextChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentService {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 80;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentInfoRepository documentInfoRepository;
    private final TextChunkRepository textChunkRepository;

    public DocumentService(KnowledgeBaseRepository knowledgeBaseRepository,
                           DocumentInfoRepository documentInfoRepository,
                           TextChunkRepository textChunkRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentInfoRepository = documentInfoRepository;
        this.textChunkRepository = textChunkRepository;
    }

    /**
     * 上传并解析文档
     */
    @Transactional
    public DocumentUploadResponse uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在，ID = " + knowledgeBaseId));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String fileName = file.getOriginalFilename();
        String fileType = getFileType(fileName);

        if (!"txt".equalsIgnoreCase(fileType) && !"md".equalsIgnoreCase(fileType)) {
            throw new IllegalArgumentException("当前版本仅支持 txt 和 md 文件");
        }

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<String> chunks = splitText(content, CHUNK_SIZE, CHUNK_OVERLAP);

            DocumentInfo documentInfo = new DocumentInfo();
            documentInfo.setKnowledgeBaseId(knowledgeBase.getId());
            documentInfo.setFileName(fileName);
            documentInfo.setFileType(fileType);
            documentInfo.setFileSize(file.getSize());
            documentInfo.setChunkCount(chunks.size());
            documentInfo.setStatus("PARSED");

            DocumentInfo savedDocument = documentInfoRepository.save(documentInfo);

            List<TextChunk> textChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunkContent = chunks.get(i);

                TextChunk textChunk = new TextChunk();
                textChunk.setKnowledgeBaseId(knowledgeBase.getId());
                textChunk.setDocumentId(savedDocument.getId());
                textChunk.setChunkIndex(i);
                textChunk.setContent(chunkContent);
                textChunk.setCharCount(chunkContent.length());

                textChunks.add(textChunk);
            }

            textChunkRepository.saveAll(textChunks);

            return new DocumentUploadResponse(
                    savedDocument.getId(),
                    savedDocument.getFileName(),
                    savedDocument.getChunkCount(),
                    savedDocument.getStatus()
            );
        } catch (Exception e) {
            throw new RuntimeException("文档上传或解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据知识库 ID 查询文档列表
     */
    public List<DocumentInfo> listDocuments(Long knowledgeBaseId) {
        return documentInfoRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    /**
     * 根据文档 ID 查询切片列表
     */
    public List<TextChunk> listChunks(Long documentId) {
        return textChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
    }

    /**
     * 简单文本切分方法
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        String normalizedText = text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        int start = 0;
        int length = normalizedText.length();

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String chunk = normalizedText.substring(start, end).trim();

            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= length) {
                break;
            }

            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
        }

        return chunks;
    }

    /**
     * 获取文件后缀
     */
    private String getFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}