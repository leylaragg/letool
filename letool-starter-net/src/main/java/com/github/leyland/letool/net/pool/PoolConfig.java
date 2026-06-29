package com.github.leyland.letool.net.pool;

/**
 * 通用连接池配置模型 —— 控制最小空闲、最大总数、等待超时、驱逐周期等参数.
 *
 * <p>使用 Builder 模式构建，所有参数均有合理的默认值：</p>
 * <pre>{@code
 * PoolConfig config = PoolConfig.builder()
 *         .minIdle(2)
 *         .maxTotal(10)
 *         .maxWaitMs(3000)
 *         .build();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class PoolConfig {

    // ======================== 配置字段 ========================

    /** 最小空闲连接数，默认 2 */
    private int minIdle = 2;

    /** 最大连接总数，默认 10 */
    private int maxTotal = 10;

    /** 获取连接的最大等待时间（毫秒），默认 5000 */
    private long maxWaitMs = 5000;

    /** 空闲连接驱逐周期（毫秒），默认 60000（1 分钟） */
    private long evictionIntervalMs = 60_000;

    // ======================== 构造器 ========================

    /**
     * 无参构造 —— 使用所有默认值.
     */
    public PoolConfig() {
    }

    /**
     * 全参构造 —— 通过 Builder 调用.
     *
     * @param minIdle            最小空闲连接数
     * @param maxTotal           最大连接总数
     * @param maxWaitMs          最大等待毫秒数
     * @param evictionIntervalMs 驱逐间隔毫秒数
     */
    public PoolConfig(int minIdle, int maxTotal, long maxWaitMs, long evictionIntervalMs) {
        this.minIdle = minIdle;
        this.maxTotal = maxTotal;
        this.maxWaitMs = maxWaitMs;
        this.evictionIntervalMs = evictionIntervalMs;
    }

    // ======================== Builder ========================

    /**
     * 创建 Builder 实例.
     *
     * @return PoolConfig 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * PoolConfig 的 Builder 模式构造器.
     */
    public static class Builder {
        private int minIdle = 2;
        private int maxTotal = 10;
        private long maxWaitMs = 5000;
        private long evictionIntervalMs = 60_000;

        /**
         * 设置最小空闲连接数.
         *
         * @param minIdle 最小空闲连接数
         * @return this
         */
        public Builder minIdle(int minIdle) {
            this.minIdle = minIdle;
            return this;
        }

        /**
         * 设置最大连接总数.
         *
         * @param maxTotal 最大连接总数
         * @return this
         */
        public Builder maxTotal(int maxTotal) {
            this.maxTotal = maxTotal;
            return this;
        }

        /**
         * 设置获取连接的最大等待时间（毫秒）.
         *
         * @param maxWaitMs 最大等待毫秒数
         * @return this
         */
        public Builder maxWaitMs(long maxWaitMs) {
            this.maxWaitMs = maxWaitMs;
            return this;
        }

        /**
         * 设置空闲连接驱逐周期（毫秒）.
         *
         * @param evictionIntervalMs 驱逐间隔毫秒数
         * @return this
         */
        public Builder evictionIntervalMs(long evictionIntervalMs) {
            this.evictionIntervalMs = evictionIntervalMs;
            return this;
        }

        /**
         * 构建 PoolConfig 实例.
         *
         * @return 配置实例
         */
        public PoolConfig build() {
            return new PoolConfig(minIdle, maxTotal, maxWaitMs, evictionIntervalMs);
        }
    }

    // ======================== Getter / Setter ========================

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    public long getMaxWaitMs() {
        return maxWaitMs;
    }

    public void setMaxWaitMs(long maxWaitMs) {
        this.maxWaitMs = maxWaitMs;
    }

    public long getEvictionIntervalMs() {
        return evictionIntervalMs;
    }

    public void setEvictionIntervalMs(long evictionIntervalMs) {
        this.evictionIntervalMs = evictionIntervalMs;
    }
}
