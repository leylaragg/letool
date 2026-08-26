package io.github.leylaragg.letool.lock.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证通用锁模块只保留与具体后端无关的开关。 */
class LockPropertiesTest {

    /** 默认启用锁与幂等能力。 */
    @Test
    void defaultsShouldEnableGenericCapabilities() {
        LockProperties properties = new LockProperties();
        assertTrue(properties.isEnabled());
        assertNotNull(properties.getIdempotent());
        assertTrue(properties.getIdempotent().isEnabled());
    }

    /** 两个功能开关应能独立绑定。 */
    @Test
    void switchesShouldBeMutable() {
        LockProperties properties = new LockProperties();
        properties.setEnabled(false);
        properties.getIdempotent().setEnabled(false);
        assertFalse(properties.isEnabled());
        assertFalse(properties.getIdempotent().isEnabled());
    }
}
