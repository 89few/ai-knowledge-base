package org.aiknowledgebase.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    public RateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 简单固定窗口限流。
     *
     * @param clientKey   用户标识，例如 sessionId 或 IP
     * @param maxRequests 窗口内最大请求数
     * @param window      时间窗口
     * @return true 表示允许请求，false 表示请求过于频繁
     */
    public boolean tryAcquire(String clientKey, int maxRequests, Duration window) {
        String redisKey = "rate_limit:" + clientKey;

        Long count = stringRedisTemplate.opsForValue().increment(redisKey);

        if (count != null && count == 1) {
            stringRedisTemplate.expire(redisKey, window);
        }

        return count != null && count <= maxRequests;
    }
}