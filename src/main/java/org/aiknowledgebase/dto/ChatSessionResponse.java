package org.aiknowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChatSessionResponse {

    private String sessionId;

    private String title;

    private Long messageCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}