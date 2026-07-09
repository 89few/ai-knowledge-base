package org.aiknowledgebase.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "kb_uploaded_file")
public class UploadedFileInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 上传用户 ID
     */
    @Column
    private Long userId;

    /**
     * 默认知识库 ID，目前先用 1
     */
    @Column(nullable = false)
    private Long knowledgeBaseId;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(length = 50)
    private String fileType;

    @Column
    private Long fileSize;

    /**
     * SUCCESS / FAILED
     */
    @Column(length = 30)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
    }
}