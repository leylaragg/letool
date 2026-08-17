package io.github.leylaragg.letool.thread.propagation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MDC 任务装饰器的上下文隔离测试。
 */
class MdcTaskDecoratorTest {

    /**
     * 每个用例结束后清理当前线程上下文，避免测试相互污染。
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * 验证提交线程没有 MDC 时，任务执行期间不会看到工作线程残留上下文，
     * 且执行完成后会恢复工作线程原始上下文。
     */
    @Test
    void decorateShouldClearWorkerContextWhenSubmitterContextIsEmpty() {
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        Runnable decorated = decorator.decorate(() -> assertNull(MDC.get("traceId")));
        MDC.put("traceId", "worker-context");

        decorated.run();

        assertEquals("worker-context", MDC.get("traceId"));
    }

    /**
     * 验证任务使用提交时的 MDC 快照，后续修改提交线程上下文不会影响已装饰任务。
     */
    @Test
    void decorateShouldUseSubmissionSnapshot() {
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<String> observedTraceId = new AtomicReference<>();
        MDC.put("traceId", "submitted-context");
        Runnable decorated = decorator.decorate(
                () -> observedTraceId.set(MDC.get("traceId"))
        );
        MDC.put("traceId", "changed-context");

        decorated.run();

        assertEquals("submitted-context", observedTraceId.get());
        assertEquals("changed-context", MDC.get("traceId"));
    }
}
