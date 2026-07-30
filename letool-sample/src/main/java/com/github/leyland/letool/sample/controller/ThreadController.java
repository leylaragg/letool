package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.thread.annotation.AsyncWithContext;
import com.github.leyland.letool.tool.model.R;
import com.github.leyland.letool.tool.util.IdUtil;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * 演示 letool-starter-thread 核心功能：使用 {@link AsyncWithContext}
 * 组合注解切换到默认异步执行器并传播 MDC。
 */
@RestController
@RequestMapping("/api/public/thread")
public class ThreadController {

    /**
     * 使用默认 {@code letoolTaskExecutor} 异步执行并传播调用线程的 MDC。
     *
     * @return 异步响应
     */
    @AsyncWithContext
    @GetMapping("/async")
    public CompletableFuture<R<String>> async() {
        String traceId = IdUtil.simpleUUID();
        return CompletableFuture.completedFuture(R.ok("Async task completed with traceId: " + traceId));
    }
}
