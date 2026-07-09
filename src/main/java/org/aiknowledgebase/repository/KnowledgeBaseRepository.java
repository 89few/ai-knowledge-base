package org.aiknowledgebase.repository;

import org.aiknowledgebase.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
}