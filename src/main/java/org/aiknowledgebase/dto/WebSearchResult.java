package org.aiknowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WebSearchResult {

    /**
     * 网页标题
     */
    private String title;

    /**
     * 网页链接
     */
    private String url;

    /**
     * Tavily 返回的网页摘要或正文片段
     */
    private String content;

    /**
     * 搜索相关性分数
     */
    private Double score;

    /**
     * 发布时间，有些网页可能为空
     */
    private String publishedDate;
}