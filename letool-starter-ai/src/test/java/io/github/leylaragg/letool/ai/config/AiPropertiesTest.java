package io.github.leylaragg.letool.ai.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AiProperties} 新路由属性测试。
 */
class AiPropertiesTest {

    /**
     * 验证新建配置保持预期的路由默认值。
     */
    @Test
    void shouldProvideRoutingDefaults() {
        AiProperties properties = new AiProperties();

        assertTrue(properties.isEnabled());
        assertNull(properties.getDefaultChatModel());
        assertNull(properties.getDefaultEmbeddingModel());
    }

    /**
     * 验证路由属性的 getter 和 setter 原样保存配置值。
     */
    @Test
    void shouldRetainRoutingValues() {
        AiProperties properties = new AiProperties();

        properties.setEnabled(false);
        properties.setDefaultChatModel("primary-chat");
        properties.setDefaultEmbeddingModel("primary-embedding");

        assertFalse(properties.isEnabled());
        assertEquals("primary-chat", properties.getDefaultChatModel());
        assertEquals("primary-embedding", properties.getDefaultEmbeddingModel());
    }
}
