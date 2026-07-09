package org.aiknowledgebase.repository;

import org.aiknowledgebase.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findBySessionId(String sessionId);

    Optional<ChatSession> findBySessionIdAndUserId(String sessionId, Long userId);

    boolean existsBySessionId(String sessionId);

    List<ChatSession> findAllByOrderByUpdateTimeDesc();

    List<ChatSession> findByUserIdOrderByUpdateTimeDesc(Long userId);

    void deleteBySessionId(String sessionId);

    void deleteBySessionIdAndUserId(String sessionId, Long userId);
}