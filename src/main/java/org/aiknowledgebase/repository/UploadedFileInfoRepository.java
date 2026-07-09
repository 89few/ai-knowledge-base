package org.aiknowledgebase.repository;

import org.aiknowledgebase.entity.UploadedFileInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadedFileInfoRepository extends JpaRepository<UploadedFileInfo, Long> {

    List<UploadedFileInfo> findByKnowledgeBaseIdOrderByCreateTimeDesc(Long knowledgeBaseId);
}