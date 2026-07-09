package org.aiknowledgebase.service;

import org.aiknowledgebase.dto.ChatMessageResponse;
import org.aiknowledgebase.dto.ChatSessionResponse;
import org.aiknowledgebase.entity.ChatMessage;
import org.aiknowledgebase.entity.ChatSession;
import org.aiknowledgebase.repository.ChatMessageRepository;
import org.aiknowledgebase.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatSessionService(ChatSessionRepository chatSessionRepository,
                              ChatMessageRepository chatMessageRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * 查询当前登录用户的历史会话。
     */
    public List<ChatSessionResponse> listSessions(Long userId) {
        List<ChatSession> sessions = chatSessionRepository.findByUserIdOrderByUpdateTimeDesc(userId);

        return sessions.stream()
                .map(session -> new ChatSessionResponse(
                        session.getSessionId(),
                        session.getTitle(),
                        chatMessageRepository.countBySessionId(session.getSessionId()),
                        session.getCreateTime(),
                        session.getUpdateTime()
                ))
                .toList();
    }

    /**
     * 查询当前用户某个会话的消息。
     */
    public List<ChatMessageResponse> listMessages(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        chatSessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权访问"));

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);

        return messages.stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getSessionId(),
                        message.getRole(),
                        message.getContent(),
                        message.getCreateTime()
                ))
                .toList();
    }

    /**
     * 删除当前用户某个会话。
     */
    @Transactional
    public void deleteSession(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        chatSessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权访问"));

        chatMessageRepository.deleteBySessionId(sessionId);
        chatSessionRepository.deleteBySessionIdAndUserId(sessionId, userId);
    }
}