package io.github.leylaragg.letool.exception.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * {@code letool.exception} 前缀下的异常消息解析配置。
 *
 * <p>默认启用 Starter 国际化消息；请求上下文没有语言环境时，固定使用简体中文。</p>
 */
@ConfigurationProperties(prefix = "letool.exception")
public class ExceptionProperties {

    /** 是否注册异常自动配置提供的基础 Bean。 */
    private boolean enabled = true;

    /** 自动配置消息解析器使用的国际化设置。 */
    private I18n i18n = new I18n();

    /**
     * 判断是否启用异常基础设施自动配置。
     *
     * @return 默认返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 启用或禁用全部异常基础设施自动配置。
     *
     * @param enabled 传入 {@code true} 时注册异常基础设施 Bean
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取消息解析器使用的国际化设置。
     *
     * @return 已按文档默认值初始化的国际化设置
     */
    public I18n getI18n() {
        return i18n;
    }

    /**
     * 替换国际化设置，通常由配置属性绑定过程调用。
     *
     * @param i18n 要使用的国际化设置
     */
    public void setI18n(I18n i18n) {
        this.i18n = i18n;
    }

    /**
     * 控制国际化资源查找，以及脱离请求上下文时使用的语言环境。
     */
    public static class I18n {

        /** 是否从应用和 Starter 资源包中解析消息码。 */
        private boolean enabled = true;

        /** 未显式指定且请求上下文也没有语言环境时使用的默认值。 */
        private Locale defaultLocale = Locale.SIMPLIFIED_CHINESE;

        /** Starter 资源查找失败时是否允许回退到 JVM 默认语言环境。 */
        private boolean fallbackToSystemLocale = false;

        /**
         * 判断是否从国际化资源包中解析消息码。
         *
         * @return 默认返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 启用国际化资源查找；关闭后仅使用稳定的默认消息。
         *
         * @param enabled 传入 {@code true} 时解析国际化资源消息
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取未显式指定且请求上下文也没有语言环境时使用的默认语言环境。
         *
         * @return 默认为简体中文
         */
        public Locale getDefaultLocale() {
            return defaultLocale;
        }

        /**
         * 设置脱离显式或请求绑定语言环境时使用的默认语言环境。
         *
         * @param defaultLocale 消息解析使用的默认语言环境
         */
        public void setDefaultLocale(Locale defaultLocale) {
            this.defaultLocale = defaultLocale;
        }

        /**
         * 判断 Starter 消息未命中时是否可以使用 JVM 默认语言环境。
         *
         * @return 为保证解析结果确定，默认返回 {@code false}
         */
        public boolean isFallbackToSystemLocale() {
            return fallbackToSystemLocale;
        }

        /**
         * 控制是否允许从请求语言环境回退到 JVM 默认语言环境。
         *
         * @param fallbackToSystemLocale 传入 {@code true} 时允许回退
         */
        public void setFallbackToSystemLocale(boolean fallbackToSystemLocale) {
            this.fallbackToSystemLocale = fallbackToSystemLocale;
        }
    }
}
