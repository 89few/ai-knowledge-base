package org.aiknowledgebase.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.aiknowledgebase.dto.WebSearchResult;
import org.aiknowledgebase.entity.UserAccount;
import org.aiknowledgebase.service.AuthService;
import org.aiknowledgebase.service.RateLimitService;
import org.aiknowledgebase.service.WebSearchService;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/web-search")
public class WebSearchController {

    private final WebSearchService webSearchService;
    private final AuthService authService;
    private final RateLimitService rateLimitService;

    public WebSearchController(WebSearchService webSearchService,
                               AuthService authService,
                               RateLimitService rateLimitService) {
        this.webSearchService = webSearchService;
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 单独测试 Tavily 联网搜索。
     */
    @GetMapping("/search")
    public List<WebSearchResult> search(@RequestParam String query,
                                        @RequestParam(required = false) Integer maxResults,
                                        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                        HttpServletRequest request) {

        UserAccount currentUser = authService.resolveUser(authorizationHeader);

        String clientKey = "web-search:user:" + currentUser.getId();

        boolean allowed = rateLimitService.tryAcquire(
                clientKey,
                5,
                Duration.ofMinutes(1)
        );

        if (!allowed) {
            throw new IllegalArgumentException("联网搜索请求过于频繁，请稍后再试。");
        }

        return webSearchService.search(query, maxResults);
    }
}