package org.aiknowledgebase.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb_document_info")
public class DocumentInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属知识库 ID
     */
    @Column(nullable = false)
    private Long knowledgeBaseId;

    /**
     * 原始文件名
     */
    @Column(nullable = false, length = 255)
    private String fileName;

    /**
     * 文件类型，例如 txt、md
     */
    @Column(length = 50)
    private String fileType;

    /**
     * 文件大小，单位 byte
     */
    private Long fileSize;

    /**
     * 文档切片数量
     */
    private Integer chunkCount;

    /**
     * 文档状态：UPLOADED / PARSED / FAILED
     */
    @Column(length = 50)
    private String status;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;
        if (this.status == null) {
            this.status = "UPLOADED";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}