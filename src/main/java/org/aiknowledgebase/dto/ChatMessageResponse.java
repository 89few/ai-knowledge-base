package org.aiknowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChatMessageResponse {

    private Long id;

    private String sessionId;

    private String role;

    private String content;

    private LocalDateTime createTime;
}