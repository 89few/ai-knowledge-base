package org.aiknowledgebase.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb_chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属会话 ID
     */
    @Column(nullable = false, length = 64)
    private String sessionId;

    /**
     * 角色：USER / ASSISTANT
     */
    @Column(nullable = false, length = 20)
    private String role;

    /**
     * 消息内容
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
    }
}