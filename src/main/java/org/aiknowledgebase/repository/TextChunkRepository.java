package org.aiknowledgebase.repository;

import org.aiknowledgebase.entity.TextChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TextChunkRepository extends JpaRepository<TextChunk, Long> {

    List<TextChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    List<TextChunk> findByKnowledgeBaseId(Long knowledgeBaseId);
}