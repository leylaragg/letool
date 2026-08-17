package io.github.leylaragg.letool.mq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQ 便利门面配置属性。
 *
 * <p>Letool 只拥有模块开关和 Provider 路由配置。Binding、目标、消费组、重试、死信、分区等
 * 参数继续由 {@code spring.cloud.stream.*} 原生配置管理。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.mq")
public class MqProperties {

    /** 是否显式启用 MQ 便利门面。 */
    private boolean enabled;

    /** 多 Provider 场景使用的默认 Provider 名称。 */
    private String defaultProvider;

    /**
     * 判断 MQ 便利门面是否启用。
     *
     * @return {@code true} 表示启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 MQ 便利门面开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取默认 Provider 名称。
     *
     * @return 默认 Provider 名称；未配置时返回 {@code null}
     */
    public String getDefaultProvider() {
        return defaultProvider;
    }

    /**
     * 设置默认 Provider 名称。
     *
     * @param defaultProvider 默认 Provider 名称
     */
    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }
}
