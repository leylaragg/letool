package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.ratelimiter.annotation.RateLimit;
import com.github.leyland.letool.tool.model.R;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示 letool-starter-ratelimiter 核心功能：声明式限流（@RateLimit）.
 */
@RestController
@RequestMapping("/api/public/ratelimiter")
public class RateLimiterController {

    /**
     * 基础限流 —— 每秒最多 5 次请求（超过后抛出 RateLimitException）.
     */
    @RateLimit(key = "hello-limit", permitsPerSecond = 5)
    @GetMapping("/hello")
    public R<String> hello() {
        return R.ok("Rate-limited hello! (max 5 req/s)");
    }

    /**
     * 动态 key 限流 —— 按用户 ID 限流（每个用户每秒最多 3 次）.
     */
    @RateLimit(key = "'user:' + #userId", permitsPerSecond = 3)
    @GetMapping("/user")
    public R<String> userRateLimit(@RequestParam String userId) {
        return R.ok("User " + userId + " request allowed");
    }
}
