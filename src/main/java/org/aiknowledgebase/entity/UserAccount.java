package org.aiknowledgebase.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb_user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名，唯一
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * BCrypt 加密后的密码
     */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    /**
     * 昵称
     */
    @Column(length = 50)
    private String nickname;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;

        if (this.nickname == null || this.nickname.isBlank()) {
            this.nickname = this.username;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}