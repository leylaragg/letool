package com.github.leyland.letool.thread.propagation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 编程式 MDC 上下文包装工具的隔离测试。
 */
class ContextPropagateExecutorTest {

    /**
     * 每个用例结束后清理当前线程上下文，避免测试相互污染。
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * 验证提交线程没有 MDC 时，包装任务会在执行期间清除工作线程残留值，
     * 并在执行完成后恢复原值。
     */
    @Test
    void wrapShouldClearWorkerContextWhenSubmitterContextIsEmpty() {
        Runnable wrapped = ContextPropagateExecutor.wrap(
                () -> assertNull(MDC.get("traceId"))
        );
        MDC.put("traceId", "worker-context");

        wrapped.run();

        assertEquals("worker-context", MDC.get("traceId"));
    }
}
