package org.aiknowledgebase.repository;

import org.aiknowledgebase.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 查询某个会话最近的 N 条消息，倒序返回。
     */
    List<ChatMessage> findBySessionIdOrderByCreateTimeDesc(String sessionId, Pageable pageable);

    /**
     * 兼容之前写过的最近 10 条查询。
     */
    List<ChatMessage> findTop10BySessionIdOrderByCreateTimeDesc(String sessionId);

    /**
     * 查询某个会话的全部消息，正序返回。
     */
    List<ChatMessage> findBySessionIdOrderByCreateTimeAsc(String sessionId);

    /**
     * 查询某个会话中最近一条用户消息。
     */
    Optional<ChatMessage> findFirstBySessionIdAndRoleOrderByCreateTimeDesc(String sessionId, String role);

    /**
     * 统计某个会话的消息数量。
     */
    long countBySessionId(String sessionId);

    /**
     * 删除某个会话下的全部消息。
     */
    void deleteBySessionId(String sessionId);
}