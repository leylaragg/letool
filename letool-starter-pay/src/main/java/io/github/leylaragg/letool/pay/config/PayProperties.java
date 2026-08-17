package io.github.leylaragg.letool.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付核心配置，绑定 {@code letool.pay}。
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.pay")
public class PayProperties {

    private boolean enabled;
    private String defaultProvider;
    private Mock mock = new Mock();

    /**
     * 判断支付模块是否启用。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() { return enabled; }

    /**
     * 设置支付模块开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * 获取默认 Provider 名称。
     *
     * @return 默认 Provider；未配置时返回 {@code null}
     */
    public String getDefaultProvider() { return defaultProvider; }

    /**
     * 设置默认 Provider 名称。
     *
     * @param defaultProvider Provider 名称
     */
    public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }

    /**
     * 获取 Mock Provider 配置。
     *
     * @return Mock 配置
     */
    public Mock getMock() { return mock; }

    /**
     * 设置 Mock Provider 配置。
     *
     * @param mock Mock 配置
     */
    public void setMock(Mock mock) { this.mock = mock; }

    /**
     * Mock Provider 配置。
     */
    public static class Mock {
        private boolean enabled;

        /**
         * 判断 Mock Provider 是否启用。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() { return enabled; }

        /**
         * 设置 Mock Provider 开关。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
