package com.github.leyland.letool.thread.propagation;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

import java.util.Map;

/**
 * Spring {@link TaskDecorator} 实现，在异步任务执行时传递 MDC 上下文。
 *
 * <p>配合 Spring 的 {@code @Async} 注解和 {@code ThreadPoolTaskExecutor} 使用，
 * 确保异步方法中能获取到父线程的 TraceId 等 MDC 信息。</p>
 *
 * <p>配置方式（在 ThreadPoolTaskExecutor 上设置）：</p>
 * <pre>{@code
 * @Bean("taskExecutor")
 * public Executor taskExecutor() {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(new MdcTaskDecorator());  // 关键配置
 *     executor.initialize();
 *     return executor;
 * }
 * }</pre>
 *
 * <p>执行流程：</p>
 * <ol>
 *   <li>任务提交线程调用 {@code decorate()} 时捕获 MDC 快照</li>
 *   <li>工作线程执行任务前还原 MDC 上下文</li>
 *   <li>任务执行完成后清理 MDC（避免污染线程池中的后续任务）</li>
 * </ol>
 *
 * @author leyland
 * @since 2.0.0
 */
public class MdcTaskDecorator implements TaskDecorator {

    /**
     * 装饰 Runnable，使其在工作线程中自动继承提交线程的 MDC 上下文。
     *
     * <p>注意：任务完成后会 {@link MDC#clear()} 清理上下文，
     * 避免工作线程的 MDC 在不同任务间泄漏。</p>
     *
     * @param runnable 原始任务
     * @return 包装后的任务（携带提交时刻的 MDC 快照）
     */
    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
