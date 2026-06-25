package com.github.leyland.letool.cipher.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 加密模块自动配置 —— 启用条件：{@code letool.cipher.enabled=true}（默认开启）.
 *
 * <p>所有加解密方法均为静态工具方法（{@link com.github.leyland.letool.cipher.util.CipherUtil}），
 * 无需注入 Bean。此配置类仅用于激活配置属性绑定和条件化模块启用/禁用.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CipherProperties.class)
@ConditionalOnProperty(prefix = "letool.cipher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CipherAutoConfiguration {
}
