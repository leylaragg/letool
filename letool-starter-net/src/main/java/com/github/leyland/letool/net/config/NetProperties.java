package com.github.leyland.letool.net.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Letool 网络模块基础设施配置。
 */
@ConfigurationProperties(prefix = "letool.net")
public class NetProperties {

    /** TCP 客户端共享运行时配置。 */
    private Tcp tcp = new Tcp();

    /**
     * 获取 TCP 运行时配置。
     *
     * @return TCP 运行时配置
     */
    public Tcp getTcp() {
        return tcp;
    }

    /**
     * 设置 TCP 运行时配置。
     *
     * @param tcp TCP 运行时配置
     */
    public void setTcp(Tcp tcp) {
        if (tcp == null) {
            throw new IllegalArgumentException("tcp 不能为空");
        }
        this.tcp = tcp;
    }

    /**
     * TCP 客户端共享运行时配置。
     */
    public static class Tcp {

        /** 是否启用 TCP 客户端基础设施。 */
        private boolean enabled = true;

        /** Netty 事件线程数。 */
        private int eventLoopThreads = Math.max(
                1,
                Math.min(Runtime.getRuntime().availableProcessors(), 16));

        /** 优雅关闭静默期。 */
        private Duration shutdownQuietPeriod = Duration.ofMillis(100);

        /** 优雅关闭最大等待时间。 */
        private Duration shutdownTimeout = Duration.ofSeconds(5);

        /**
         * 判断是否启用 TCP 客户端基础设施。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 TCP 客户端基础设施。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取 Netty 事件线程数。
         *
         * @return 事件线程数
         */
        public int getEventLoopThreads() {
            return eventLoopThreads;
        }

        /**
         * 设置 Netty 事件线程数。
         *
         * @param eventLoopThreads 正整数线程数
         */
        public void setEventLoopThreads(int eventLoopThreads) {
            this.eventLoopThreads = eventLoopThreads;
        }

        /**
         * 获取优雅关闭静默期。
         *
         * @return 静默期
         */
        public Duration getShutdownQuietPeriod() {
            return shutdownQuietPeriod;
        }

        /**
         * 设置优雅关闭静默期。
         *
         * @param shutdownQuietPeriod 非负静默期
         */
        public void setShutdownQuietPeriod(Duration shutdownQuietPeriod) {
            this.shutdownQuietPeriod = shutdownQuietPeriod;
        }

        /**
         * 获取优雅关闭最大等待时间。
         *
         * @return 最大等待时间
         */
        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        /**
         * 设置优雅关闭最大等待时间。
         *
         * @param shutdownTimeout 正数最大等待时间
         */
        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }
    }
}
