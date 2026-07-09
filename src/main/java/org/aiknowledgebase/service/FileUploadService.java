package org.aiknowledgebase.service;

import org.aiknowledgebase.dto.UploadedFileResponse;
import org.aiknowledgebase.entity.UploadedFileChunk;
import org.aiknowledgebase.entity.UploadedFileInfo;
import org.aiknowledgebase.repository.UploadedFileChunkRepository;
import org.aiknowledgebase.repository.UploadedFileInfoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class FileUploadService {

    @Value("${upload.chunk-size:800}")
    private int chunkSize;

    @Value("${upload.chunk-overlap:100}")
    private int chunkOverlap;

    private final FileTextExtractorService fileTextExtractorService;
    private final UploadedFileInfoRepository uploadedFileInfoRepository;
    private final UploadedFileChunkRepository uploadedFileChunkRepository;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    public FileUploadService(FileTextExtractorService fileTextExtractorService,
                             UploadedFileInfoRepository uploadedFileInfoRepository,
                             UploadedFileChunkRepository uploadedFileChunkRepository,
                             EmbeddingService embeddingService,
                             JdbcTemplate jdbcTemplate) {
        this.fileTextExtractorService = fileTextExtractorService;
        this.uploadedFileInfoRepository = uploadedFileInfoRepository;
        this.uploadedFileChunkRepository = uploadedFileChunkRepository;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public UploadedFileResponse upload(Long userId, Long knowledgeBaseId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String filename = file.getOriginalFilename();
        String fileType = fileTextExtractorService.getFileType(filename);

        UploadedFileInfo fileInfo = new UploadedFileInfo();
        fileInfo.setUserId(userId);
        fileInfo.setKnowledgeBaseId(knowledgeBaseId);
        fileInfo.setOriginalFilename(filename);
        fileInfo.setFileType(fileType);
        fileInfo.setFileSize(file.getSize());
        fileInfo.setStatus("PROCESSING");

        UploadedFileInfo savedFile = uploadedFileInfoRepository.save(fileInfo);

        try {
            String text = fileTextExtractorService.extractText(file);
            text = normalizeText(text);

            if (text.isBlank()) {
                throw new IllegalArgumentException("文件中未解析到有效文本内容");
            }

            List<String> chunks = splitText(text);

            for (int i = 0; i < chunks.size(); i++) {
                UploadedFileChunk chunk = new UploadedFileChunk();
                chunk.setUploadedFileId(savedFile.getId());
                chunk.setKnowledgeBaseId(knowledgeBaseId);
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));

                uploadedFileChunkRepository.save(chunk);
            }

            initUploadedChunkVectorColumn();
            buildEmbeddingsForFile(savedFile.getId());

            savedFile.setStatus("SUCCESS");
            uploadedFileInfoRepository.save(savedFile);

            return new UploadedFileResponse(
                    savedFile.getId(),
                    filename,
                    fileType,
                    chunks.size(),
                    "SUCCESS",
                    "文件上传、解析、切分和向量化完成"
            );

        } catch (Exception e) {
            savedFile.setStatus("FAILED");
            savedFile.setErrorMessage(e.getMessage());
            uploadedFileInfoRepository.save(savedFile);

            throw e;
        }
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();

        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();

            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= text.length()) {
                break;
            }

            start = Math.max(0, end - chunkOverlap);
        }

        return chunks;
    }

    /**
     * 给上传文件 chunk 表增加 PGVector 字段。
     */
    private void initUploadedChunkVectorColumn() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

        String sql = "ALTER TABLE kb_uploaded_file_chunk " +
                "ADD COLUMN IF NOT EXISTS embedding vector(" + EmbeddingService.VECTOR_DIM + ")";

        jdbcTemplate.execute(sql);
    }

    /**
     * 为某个上传文件的所有 chunk 构建 embedding。
     */
    private void buildEmbeddingsForFile(Long uploadedFileId) {
        String querySql = """
                SELECT id, content
                FROM kb_uploaded_file_chunk
                WHERE uploaded_file_id = ?
                ORDER BY chunk_index
                """;

        List<ChunkRow> chunks = jdbcTemplate.query(
                querySql,
                (rs, rowNum) -> new ChunkRow(
                        rs.getLong("id"),
                        rs.getString("content")
                ),
                uploadedFileId
        );

        for (ChunkRow chunk : chunks) {
            double[] embedding = embeddingService.embed(chunk.content());
            String vectorLiteral = embeddingService.toVectorLiteral(embedding);

            String updateSql = """
                    UPDATE kb_uploaded_file_chunk
                    SET embedding = CAST(? AS vector)
                    WHERE id = ?
                    """;

            jdbcTemplate.update(updateSql, vectorLiteral, chunk.id());
        }
    }

    private record ChunkRow(Long id, String content) {
    }
}