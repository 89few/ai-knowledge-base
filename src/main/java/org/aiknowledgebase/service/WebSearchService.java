package org.aiknowledgebase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aiknowledgebase.dto.WebSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WebSearchService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final int defaultMaxResults;

    public WebSearchService(@Value("${web-search.tavily.api-key}") String apiKey,
                            @Value("${web-search.tavily.base-url}") String baseUrl,
                            @Value("${web-search.tavily.max-results:5}") int defaultMaxResults) {
        this.apiKey = apiKey;
        this.defaultMaxResults = defaultMaxResults;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 执行联网搜索。
     */
    public List<WebSearchResult> search(String query, Integer maxResults) {
        String normalizedQuery = query == null ? "" : query.trim();

        if (normalizedQuery.isBlank()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 TAVILY_API_KEY，无法进行联网搜索");
        }

        int finalMaxResults = maxResults == null || maxResults <= 0
                ? defaultMaxResults
                : Math.min(maxResults, 20);

        Map<String, Object> requestBody = Map.of(
                "query", normalizedQuery,
                "search_depth", "basic",
                "max_results", finalMaxResults,
                "include_answer", false,
                "include_raw_content", false
        );

        try {
            String response = restClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseSearchResults(response);

        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new RuntimeException("Tavily 免费额度或请求频率达到限制，请稍后再试。");

        } catch (HttpClientErrorException e) {
            throw new RuntimeException(
                    "Tavily 搜索失败，状态码："
                            + e.getStatusCode()
                            + "，响应："
                            + safePreview(e.getResponseBodyAsString(), 300)
            );

        } catch (ResourceAccessException e) {
            throw new RuntimeException("Tavily 搜索失败，可能是网络超时或无法访问 Tavily。");

        } catch (Exception e) {
            throw new RuntimeException("Tavily 搜索发生未知错误：" + e.getMessage(), e);
        }
    }

    private List<WebSearchResult> parseSearchResults(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode resultsNode = root.path("results");

            List<WebSearchResult> results = new ArrayList<>();

            if (!resultsNode.isArray()) {
                return results;
            }

            for (JsonNode item : resultsNode) {
                String title = item.path("title").asText("");
                String url = item.path("url").asText("");
                String content = item.path("content").asText("");
                double score = item.path("score").asDouble(0.0);
                String publishedDate = item.path("published_date").asText("");

                if (!url.isBlank()) {
                    results.add(new WebSearchResult(
                            title,
                            url,
                            content,
                            score,
                            publishedDate
                    ));
                }
            }

            return results;

        } catch (Exception e) {
            throw new RuntimeException(
                    "解析 Tavily 搜索结果失败："
                            + e.getMessage()
                            + "；原始响应："
                            + safePreview(response, 300)
            );
        }
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