package org.aiknowledgebase.repository;

import org.aiknowledgebase.entity.UploadedFileChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadedFileChunkRepository extends JpaRepository<UploadedFileChunk, Long> {

    List<UploadedFileChunk> findByUploadedFileIdOrderByChunkIndexAsc(Long uploadedFileId);

    List<UploadedFileChunk> findByKnowledgeBaseIdOrderByIdAsc(Long knowledgeBaseId);
}