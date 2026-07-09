package org.aiknowledgebase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;

@Service
public class EmbeddingService {

    /**
     * nomic-embed-text 返回 768 维向量。
     */
    public static final int VECTOR_DIM = 768;

    private static final Duration EMBEDDING_CACHE_TTL = Duration.ofDays(7);

    @Value("${embedding.ollama.base-url}")
    private String baseUrl;

    @Value("${embedding.ollama.model}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringRedisTemplate stringRedisTemplate;

    public EmbeddingService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 使用 Ollama 本地 Embedding 模型生成真实语义向量。
     * Redis 会缓存相同文本的向量结果。
     */
    public double[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new double[VECTOR_DIM];
        }

        String normalizedText = text.trim();
        String cacheKey = buildCacheKey(normalizedText);

        double[] cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        double[] vector = callOllamaEmbedding(normalizedText);

        saveToCache(cacheKey, vector);

        return vector;
    }

    private double[] callOllamaEmbedding(String text) {
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", text
        );

        String response = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return parseEmbedding(response);
    }

    private double[] parseEmbedding(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode vectorNode = root.path("embeddings").path(0);

            if (!vectorNode.isArray()) {
                throw new IllegalStateException("Ollama 未返回有效 embeddings，原始响应：" + response);
            }

            double[] vector = new double[vectorNode.size()];

            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = vectorNode.get(i).asDouble();
            }

            if (vector.length != VECTOR_DIM) {
                throw new IllegalStateException(
                        "Embedding 维度不一致，当前模型返回 "
                                + vector.length
                                + " 维，但系统配置为 "
                                + VECTOR_DIM
                                + " 维"
                );
            }

            return vector;
        } catch (Exception e) {
            throw new RuntimeException("解析 Ollama Embedding 响应失败：" + e.getMessage(), e);
        }
    }

    private String buildCacheKey(String text) {
        return "embedding:" + model + ":" + sha256(text);
    }

    private double[] getFromCache(String cacheKey) {
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

            if (cachedJson == null || cachedJson.isBlank()) {
                return null;
            }

            return objectMapper.readValue(cachedJson, double[].class);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveToCache(String cacheKey, double[] vector) {
        try {
            String json = objectMapper.writeValueAsString(vector);
            stringRedisTemplate.opsForValue().set(cacheKey, json, EMBEDDING_CACHE_TTL);
        } catch (Exception ignored) {
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();

            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 转成 PGVector 能识别的格式：
     * [0.1,0.2,0.3,...]
     */
    public String toVectorLiteral(double[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector[i]);
        }

        sb.append("]");
        return sb.toString();
    }
}