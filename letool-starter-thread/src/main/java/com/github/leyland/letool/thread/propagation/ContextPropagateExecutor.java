package com.github.leyland.letool.thread.propagation;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 上下文传播执行器工具类，将当前线程的 MDC 上下文自动传递到子线程。
 *
 * <p>解决的问题：当使用原始线程池提交任务时，子线程不会继承父线程的
 * MDC 信息（如 TraceId），导致日志中链路追踪断掉。本工具通过在任务执行前后
 * 保存和恢复 MDC 上下文来实现透明传递。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 方式一：包装单个 Runnable
 * executor.execute(ContextPropagateExecutor.wrap(() -> {
 *     log.info("此日志自动携带父线程的 TraceId");  // MDC 已传递
 * }));
 *
 * // 方式二：包装整个 Executor
 * Executor wrapped = ContextPropagateExecutor.wrap(executor);
 * wrapped.execute(task);  // 所有通过此 Executor 的任务都自动传递 MDC
 * }</pre>
 *
 * <p>注意：上下文传播只传递 MDC 信息。如需传递安全上下文，
 * 请配合 {@link MdcTaskDecorator} 使用或自行扩展。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class ContextPropagateExecutor {

    /** 工具类，禁止实例化 */
    private ContextPropagateExecutor() {}

    /**
     * 包装 Runnable，使其执行时继承当前线程的 MDC 上下文。
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>在调用 wrap 时捕获当前线程的 MDC 上下文快照</li>
     *   <li>任务执行前：保存当前 MDC → 设置父线程 MDC</li>
     *   <li>任务执行后（finally）：恢复原始 MDC</li>
     * </ol>
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrap(Runnable task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                task.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * 包装 Executor，使所有通过该 Executor 提交的任务自动传递 MDC 上下文。
     *
     * @param executor 原始执行器
     * @return 自动传播上下文的执行器
     */
    public static Executor wrap(Executor executor) {
        return task -> executor.execute(wrap(task));
    }
}
