package io.github.leylaragg.letool.sensitive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 脱敏模块配置属性。
 */
@ConfigurationProperties(prefix = "letool.sensitive")
public class SensitiveProperties {

    /** 是否启用脱敏模块。 */
    private boolean enabled = true;

    /** Jackson 字段脱敏配置。 */
    private Jackson jackson = new Jackson();

    /**
     * 判断是否启用脱敏模块。
     *
     * @return {@code true} 表示启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置模块启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 Jackson 配置。
     *
     * @return Jackson 配置
     */
    public Jackson getJackson() {
        return jackson;
    }

    /**
     * 设置 Jackson 配置。
     *
     * @param jackson Jackson 配置
     */
    public void setJackson(Jackson jackson) {
        this.jackson = jackson;
    }

    /**
     * Jackson 字段脱敏配置。
     */
    public static class Jackson {

        /** 是否注册 Jackson 脱敏模块。 */
        private boolean enabled = true;

        /**
         * 判断是否启用 Jackson 脱敏。
         *
         * @return {@code true} 表示启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置 Jackson 脱敏启用状态。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
