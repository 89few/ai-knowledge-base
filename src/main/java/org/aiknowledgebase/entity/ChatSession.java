package org.aiknowledgebase.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb_chat_session")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 登录用户 ID。
     * 后续每个用户只能看到自己的历史会话。
     */
    @Column
    private Long userId;

    /**
     * 对外暴露的会话 ID，用 UUID 生成。
     */
    @Column(nullable = false, unique = true, length = 64)
    private String sessionId;

    /**
     * 会话标题，默认用用户第一个问题生成。
     */
    @Column(length = 200)
    private String title;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}