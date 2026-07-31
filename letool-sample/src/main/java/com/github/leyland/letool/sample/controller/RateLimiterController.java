package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.ratelimiter.annotation.RateLimit;
import com.github.leyland.letool.tool.model.R;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示 letool-starter-ratelimiter 基于 Sentinel 的声明式限流能力。
 */
@RestController
@RequestMapping("/api/public/ratelimiter")
public class RateLimiterController {

    /**
     * 基础限流：整个接口每秒最多通过 5 个许可。
     */
    @RateLimit(policy = "hello-api")
    @GetMapping("/hello")
    public R<String> hello() {
        return R.ok("Rate-limited hello! (max 5 req/s)");
    }

    /**
     * 热点参数限流：每个用户每秒最多通过 3 个许可。
     */
    @RateLimit(policy = "user-api", keyExpression = "#userId")
    @GetMapping("/user")
    public R<String> userRateLimit(@RequestParam String userId) {
        return R.ok("User " + userId + " request allowed");
    }
}
