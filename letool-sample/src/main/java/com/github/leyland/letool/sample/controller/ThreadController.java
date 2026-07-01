package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.thread.annotation.AsyncWithContext;
import com.github.leyland.letool.tool.model.R;
import com.github.leyland.letool.tool.util.IdUtil;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * 演示 letool-starter-thread 核心功能：带上下文传播的异步执行（@AsyncWithContext）.
 */
@RestController
@RequestMapping("/api/public/thread")
public class ThreadController {

    /**
     * 异步执行 —— 使用 @AsyncWithContext 在默认线程池中异步执行，自动传递 TraceId 和安全上下文.
     */
    @AsyncWithContext
    @GetMapping("/async")
    public CompletableFuture<R<String>> async() {
        String traceId = IdUtil.simpleUUID();
        return CompletableFuture.completedFuture(R.ok("Async task completed with traceId: " + traceId));
    }
}
