package io.github.leylaragg.letool.job.core;

import io.github.leylaragg.letool.job.exception.JobException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DefaultJobHandlerRegistry} 默认处理器注册表测试。
 */
class DefaultJobHandlerRegistryTest {

    /**
     * 验证注册、查询和注销形成完整生命周期。
     */
    @Test
    void shouldManageHandlerLifecycle() {
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        JobHandler handler = context -> { };

        registry.register("sync", handler);

        assertThat(registry.contains("sync")).isTrue();
        assertThat(registry.getRequired("sync")).isSameAs(handler);
        registry.unregister("sync");
        assertThat(registry.contains("sync")).isFalse();
    }

    /**
     * 验证重复任务名称和缺失处理器使用稳定错误码失败。
     */
    @Test
    void shouldRejectDuplicateAndMissingHandler() {
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        registry.register("sync", context -> { });

        assertThatThrownBy(() -> registry.register("sync", context -> { }))
                .isInstanceOf(JobException.class)
                .extracting("code")
                .isEqualTo("JOB_002");
        assertThatThrownBy(() -> registry.getRequired("missing"))
                .isInstanceOf(JobException.class)
                .extracting("code")
                .isEqualTo("JOB_005");
    }
}
