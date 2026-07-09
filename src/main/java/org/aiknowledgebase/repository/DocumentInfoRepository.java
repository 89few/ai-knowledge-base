package org.aiknowledgebase.repository;

import org.aiknowledgebase.entity.DocumentInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentInfoRepository extends JpaRepository<DocumentInfo, Long> {

    List<DocumentInfo> findByKnowledgeBaseId(Long knowledgeBaseId);
}