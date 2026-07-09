package org.aiknowledgebase.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class RedisTestController {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisTestController(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @GetMapping("/redis/test")
    public String testRedis() {
        stringRedisTemplate.opsForValue().set(
                "test:hello",
                "Redis is working",
                Duration.ofMinutes(5)
        );

        return stringRedisTemplate.opsForValue().get("test:hello");
    }
}