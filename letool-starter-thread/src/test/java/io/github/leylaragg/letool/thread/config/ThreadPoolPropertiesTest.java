package io.github.leylaragg.letool.thread.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线程池配置对象的空值防御测试。
 */
class ThreadPoolPropertiesTest {

    /**
     * 验证外部配置代码传入空聚合对象时会恢复安全默认值，
     * 避免自动配置阶段出现空指针异常。
     */
    @Test
    void aggregateSettersShouldNormalizeNullValues() {
        ThreadPoolProperties properties = new ThreadPoolProperties();

        properties.setPools(null);
        properties.setMonitoring(null);
        properties.setContextPropagation(null);

        assertNotNull(properties.getPools());
        assertTrue(properties.getPools().isEmpty());
        assertNotNull(properties.getMonitoring());
        assertNotNull(properties.getContextPropagation());
    }
}
