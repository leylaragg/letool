package com.github.leyland.letool.log.trace;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 线程池 MDC 传递装饰器 —— 将父线程的 MDC 上下文（含 TraceId）传递给子线程.
 *
 * <h3>为什么需要？</h3>
 * <p>当使用 @Async 或 ThreadPoolTaskExecutor 时，任务在子线程中执行。
 * 子线程默认不继承父线程的 MDC，导致异步任务中的日志丢失 TraceId。
 * 本装饰器通过 TaskDecorator 机制在任务提交时捕获父线程 MDC，子线程执行时恢复。</p>
 *
 * <h3>配置方式</h3>
 * <pre>{@code
 * spring.task.execution.pool.task-decorator=com.github.leyland.letool.log.trace.MdcTaskDecorator
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>仅传递任务提交瞬间的 MDC 快照，父线程后续的 MDC 变更不会反映到子线程</li>
 *   <li>子线程执行完成后自动清理 MDC，防止线程复用时污染下一任务</li>
 * </ul>
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // ==== 在父线程中捕获 MDC 快照 ====
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            // ==== 在子线程中恢复 MDC ====
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                runnable.run();
            } finally {
                // ==== 清理 MDC —— 防止线程复用时污染下一任务 ====
                MDC.clear();
            }
        };
    }
}
