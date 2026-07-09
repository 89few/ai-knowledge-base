package org.aiknowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RagSource {

    /**
     * 文本切片 ID
     */
    private Long chunkId;

    /**
     * 文档 ID
     */
    private Long documentId;

    /**
     * 切片序号
     */
    private Integer chunkIndex;

    /**
     * 检索距离，越小越相似
     */
    private Double distance;

    /**
     * 被检索到的文本内容
     */
    private String content;
}