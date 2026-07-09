package org.aiknowledgebase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aiknowledgebase.dto.RagAnswerResponse;
import org.aiknowledgebase.entity.UserAccount;
import org.aiknowledgebase.service.AuthService;
import org.aiknowledgebase.service.RagService;
import org.aiknowledgebase.service.RateLimitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final RateLimitService rateLimitService;
    private final AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagController(RagService ragService,
                         RateLimitService rateLimitService,
                         AuthService authService) {
        this.ragService = ragService;
        this.rateLimitService = rateLimitService;
        this.authService = authService;
    }

    /**
     * 普通非流式 RAG 问答接口。
     * 保留这个接口是为了兼容旧前端或调试。
     */
    @GetMapping("/ask")
    public RagAnswerResponse ask(@RequestParam Long knowledgeBaseId,
                                 @RequestParam String question,
                                 @RequestParam(defaultValue = "3") int topK,
                                 @RequestParam(required = false) String sessionId,
                                 @RequestParam(required = false) Long uploadedFileId,
                                 @RequestParam(defaultValue = "false") boolean webSearch,
                                 @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                 HttpServletRequest request) {

        UserAccount currentUser;

        try {
            currentUser = authService.resolveUser(authorizationHeader);
        } catch (Exception e) {
            return new RagAnswerResponse(
                    sessionId,
                    question,
                    "请先登录后再使用知识库问答功能。",
                    List.of()
            );
        }

        String clientKey = buildClientKey(currentUser.getId(), sessionId, request);

        boolean allowed = rateLimitService.tryAcquire(
                clientKey,
                3,
                Duration.ofSeconds(10)
        );

        if (!allowed) {
            return new RagAnswerResponse(
                    sessionId,
                    question,
                    "你发送得太频繁了，请稍等几秒再试。",
                    List.of()
            );
        }

        return ragService.ask(
                currentUser.getId(),
                knowledgeBaseId,
                question,
                topK,
                sessionId,
                uploadedFileId,
                webSearch
        );
    }

    /**
     * 流式 RAG 问答接口。
     * 前端通过这个接口实现“边生成边显示”。
     */
    @GetMapping(value = "/ask/stream", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> askStream(@RequestParam Long knowledgeBaseId,
                                                           @RequestParam String question,
                                                           @RequestParam(defaultValue = "3") int topK,
                                                           @RequestParam(required = false) String sessionId,
                                                           @RequestParam(required = false) Long uploadedFileId,
                                                           @RequestParam(defaultValue = "false") boolean webSearch,
                                                           @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                                           HttpServletRequest request) {

        UserAccount currentUser;

        try {
            currentUser = authService.resolveUser(authorizationHeader);
        } catch (Exception e) {
            StreamingResponseBody body = outputStream -> {
                writeStreamEvent(outputStream, "error", "请先登录后再使用知识库问答功能。");
                writeStreamEvent(outputStream, "done", "done");
            };

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body);
        }

        String clientKey = buildClientKey(currentUser.getId(), sessionId, request);

        boolean allowed = rateLimitService.tryAcquire(
                clientKey,
                3,
                Duration.ofSeconds(10)
        );

        if (!allowed) {
            StreamingResponseBody body = outputStream -> {
                writeStreamEvent(outputStream, "error", "你发送得太频繁了，请稍等几秒再试。");
                writeStreamEvent(outputStream, "done", "done");
            };

            return ResponseEntity.status(429)
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body);
        }

        StreamingResponseBody body = outputStream -> ragService.streamAsk(
                currentUser.getId(),
                knowledgeBaseId,
                question,
                topK,
                sessionId,
                uploadedFileId,
                webSearch,
                (type, data) -> writeStreamEvent(outputStream, type, data)
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .header("Cache-Control", "no-cache")
                .body(body);
    }

    private void writeStreamEvent(OutputStream outputStream, String type, Object data) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", type);
            event.put("data", data);

            synchronized (outputStream) {
                outputStream.write(objectMapper.writeValueAsBytes(event));
                outputStream.write('\n');
                outputStream.flush();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildClientKey(Long userId, String sessionId, HttpServletRequest request) {
        if (userId != null) {
            return "user:" + userId;
        }

        if (sessionId != null && !sessionId.isBlank()) {
            return "session:" + sessionId;
        }

        return "ip:" + request.getRemoteAddr();
    }
}