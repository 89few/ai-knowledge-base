package org.aiknowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurrentUserResponse {

    private Long userId;

    private String username;

    private String nickname;
}