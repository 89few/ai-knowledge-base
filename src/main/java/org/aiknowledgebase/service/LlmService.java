package org.aiknowledgebase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class LlmService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public LlmService(@Value("${llm.openrouter.api-key}") String apiKey,
                      @Value("${llm.openrouter.base-url}") String baseUrl,
                      @Value("${llm.openrouter.model}") String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 普通非流式调用。
     */
    public String chat(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置 OPENROUTER_API_KEY 环境变量，无法调用大模型。";
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "你是一个严谨的中文知识库问答助手。请直接给出最终答案，不要输出思考过程。"
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "temperature", 0.2,
                "max_tokens", 800,
                "reasoning", Map.of(
                        "enabled", false
                )
        );

        try {
            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "http://localhost:8080")
                    .header("X-Title", "AI Knowledge Base")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseAnswer(response);

        } catch (HttpClientErrorException.TooManyRequests e) {
            return "当前免费大模型调用次数已达上限，请稍后再试。你也可以切换到本地 Ollama 聊天模型，避免免费 API 限制。";

        } catch (HttpClientErrorException e) {
            return "调用大模型失败，状态码："
                    + e.getStatusCode()
                    + "，响应："
                    + safePreview(e.getResponseBodyAsString(), 300);

        } catch (ResourceAccessException e) {
            return "调用大模型失败，可能是网络超时或无法访问 OpenRouter，请稍后重试。";

        } catch (Exception e) {
            return "调用大模型时发生未知错误：" + e.getMessage();
        }
    }

    /**
     * 流式调用 OpenRouter。
     *
     * @param prompt 发送给大模型的 Prompt
     * @param onDelta 每生成一小段内容就回调一次
     * @return 完整回答，用于保存到数据库
     */
    public String streamChat(String prompt, Consumer<String> onDelta) {
        if (apiKey == null || apiKey.isBlank()) {
            String msg = "未配置 OPENROUTER_API_KEY 环境变量，无法调用大模型。";
            onDelta.accept(msg);
            return msg;
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "stream", true,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "你是一个严谨的中文知识库问答助手。请直接给出最终答案，不要输出思考过程。"
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "temperature", 0.2,
                    "max_tokens", 800,
                    "reasoning", Map.of(
                            "enabled", false
                    )
            );

            String json = objectMapper.writeValueAsString(requestBody);

            // OpenRouter's streaming endpoint may reset some HTTP/2 streams with
            // RST_STREAM(PROTOCOL_ERROR). HTTP/1.1 is more broadly compatible
            // with SSE streams and avoids that intermittent upstream failure.
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildChatCompletionsUrl()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "http://localhost:8080")
                    .header("X-Title", "AI Knowledge Base")
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() == 429) {
                String msg = "当前免费大模型调用次数已达上限，请稍后再试。";
                onDelta.accept(msg);
                return msg;
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String error = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                String msg = "调用大模型失败，状态码："
                        + response.statusCode()
                        + "，响应："
                        + safePreview(error, 300);
                onDelta.accept(msg);
                return msg;
            }

            StringBuilder fullAnswer = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.isBlank()) {
                        continue;
                    }

                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String data = line.substring("data:".length()).trim();

                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    try {
                        JsonNode root = objectMapper.readTree(data);

                        JsonNode deltaNode = root
                                .path("choices")
                                .path(0)
                                .path("delta");

                        String content = deltaNode.path("content").asText("");

                        if (content != null && !content.isEmpty()) {
                            fullAnswer.append(content);
                            onDelta.accept(content);
                        }

                    } catch (Exception ignored) {
                    }
                }
            }

            if (fullAnswer.length() == 0) {
                String msg = "模型未返回有效回答，请稍后重试。";
                onDelta.accept(msg);
                return msg;
            }

            return fullAnswer.toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            String msg = "流式请求被中断，通常是请求超时、浏览器刷新、页面关闭，或者后端流式任务被提前取消。请稍后重试。";
            onDelta.accept(msg);
            return msg;

        } catch (Exception e) {
            String msg = "流式调用大模型失败：" + e.getClass().getName()
                    + (e.getMessage() == null ? "" : "：" + e.getMessage());
            onDelta.accept(msg);
            return msg;
        }
    }

    private String parseAnswer(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            JsonNode messageNode = root
                    .path("choices")
                    .path(0)
                    .path("message");

            String content = messageNode.path("content").asText("");

            if (content != null && !content.isBlank()) {
                return content.trim();
            }

            String reasoning = messageNode.path("reasoning").asText("");
            if (reasoning != null && !reasoning.isBlank()) {
                return reasoning.trim();
            }

            return "模型未返回有效回答，请稍后重试或更换模型。";

        } catch (Exception e) {
            return "解析大模型响应失败："
                    + e.getMessage()
                    + "；原始响应："
                    + safePreview(response, 300);
        }
    }

    private String buildChatCompletionsUrl() {
        String cleanBaseUrl = baseUrl;

        while (cleanBaseUrl.endsWith("/")) {
            cleanBaseUrl = cleanBaseUrl.substring(0, cleanBaseUrl.length() - 1);
        }

        return cleanBaseUrl + "/chat/completions";
    }

    private String safePreview(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        String cleaned = text.replace("\n", " ").replace("\r", " ").trim();

        if (cleaned.length() <= maxLength) {
            return cleaned;
        }

        return cleaned.substring(0, maxLength);
    }
}
