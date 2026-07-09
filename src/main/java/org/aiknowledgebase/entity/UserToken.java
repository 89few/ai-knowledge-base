package org.aiknowledgebase.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb_user_token")
public class UserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户 ID
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 登录 token
     */
    @Column(nullable = false, unique = true, length = 100)
    private String token;

    /**
     * token 过期时间
     */
    @Column(nullable = false)
    private LocalDateTime expireTime;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
    }
}