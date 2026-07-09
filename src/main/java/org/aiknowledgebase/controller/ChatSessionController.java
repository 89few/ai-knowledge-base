package org.aiknowledgebase.controller;

import org.aiknowledgebase.dto.ChatMessageResponse;
import org.aiknowledgebase.dto.ChatSessionResponse;
import org.aiknowledgebase.entity.UserAccount;
import org.aiknowledgebase.service.AuthService;
import org.aiknowledgebase.service.ChatSessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final AuthService authService;

    public ChatSessionController(ChatSessionService chatSessionService,
                                 AuthService authService) {
        this.chatSessionService = chatSessionService;
        this.authService = authService;
    }

    /**
     * 查询当前用户的历史会话列表。
     */
    @GetMapping
    public List<ChatSessionResponse> listSessions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        UserAccount user = authService.resolveUser(authorizationHeader);
        return chatSessionService.listSessions(user.getId());
    }

    /**
     * 查询当前用户某个会话的全部消息。
     */
    @GetMapping("/{sessionId}/messages")
    public List<ChatMessageResponse> listMessages(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        UserAccount user = authService.resolveUser(authorizationHeader);
        return chatSessionService.listMessages(user.getId(), sessionId);
    }

    /**
     * 删除当前用户的某个历史会话。
     */
    @DeleteMapping("/{sessionId}")
    public Map<String, Object> deleteSession(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        UserAccount user = authService.resolveUser(authorizationHeader);
        chatSessionService.deleteSession(user.getId(), sessionId);

        return Map.of("message", "会话已删除");
    }
}