package org.aiknowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RagAnswerResponse {

    /**
     * 当前会话 ID。
     * 下一轮连续对话时，前端/curl 需要继续带上这个 sessionId。
     */
    private String sessionId;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 大模型回答
     */
    private String answer;

    /**
     * 引用来源
     */
    private List<RagSource> sources;
}