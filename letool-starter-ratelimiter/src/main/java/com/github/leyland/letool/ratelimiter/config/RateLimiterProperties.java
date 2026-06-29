package com.github.leyland.letool.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * letool-starter-ratelimiter 模块的全局配置属性类。
 *
 * <p>通过 Spring Boot 的 {@code @ConfigurationProperties} 机制绑定
 * {@code letool.rate-limiter} 前缀下的所有配置项，包含以下子模块配置：</p>
 *
 * <ul>
 *   <li><b>全局设置</b>：控制是否启用限流模块、默认算法类型</li>
 *   <li><b>TokenBucket（令牌桶）</b>：容量、令牌补充速率</li>
 *   <li><b>SlidingWindow（滑动窗口）</b>：窗口大小、最大许可数</li>
 *   <li><b>CircuitBreaker（熔断器）</b>：失败阈值、窗口大小、恢复超时、半开最大请求数</li>
 * </ul>
 *
 * <p>使用示例（application.yml）：</p>
 * <pre>{@code
 * letool:
 *   rate-limiter:
 *     enabled: true
 *     default-algorithm: token-bucket
 *     token-bucket:
 *       capacity: 100
 *       refill-rate: 10
 *     sliding-window:
 *       window-size: 60
 *       max-permits: 100
 *     circuit-breaker:
 *       failure-threshold: 0.5
 *       window-size: 60
 *       recovery-timeout: 60
 *       half-open-max-requests: 3
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.rate-limiter")
public class RateLimiterProperties {

    // ======================== 顶层属性 ========================

    /** 全局开关：是否启用限流熔断模块，默认 {@code true} */
    private boolean enabled = true;

    /**
     * 默认限流算法。
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>{@code "token-bucket"} —— 令牌桶算法（默认）</li>
     *   <li>{@code "sliding-window"} —— 滑动窗口算法</li>
     *   <li>{@code "leaky-bucket"} —— 漏桶算法（预留）</li>
     * </ul>
     */
    private String defaultAlgorithm = "token-bucket";

    /** 令牌桶子配置 */
    private TokenBucket tokenBucket = new TokenBucket();

    /** 滑动窗口子配置 */
    private SlidingWindow slidingWindow = new SlidingWindow();

    /** 熔断器子配置 */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    // ======================== Getter / Setter ========================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    public void setDefaultAlgorithm(String defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
    }

    public TokenBucket getTokenBucket() {
        return tokenBucket;
    }

    public void setTokenBucket(TokenBucket tokenBucket) {
        this.tokenBucket = tokenBucket;
    }

    public SlidingWindow getSlidingWindow() {
        return slidingWindow;
    }

    public void setSlidingWindow(SlidingWindow slidingWindow) {
        this.slidingWindow = slidingWindow;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    // ======================== 内部类：令牌桶配置 ========================

    /**
     * 令牌桶（Token Bucket）算法相关配置。
     *
     * <p>令牌桶算法以恒定速率向桶中放入令牌，请求到达时需从桶中获取令牌。
     * 如果桶为空则拒绝请求。该算法允许一定程度的突发流量（桶容量即为最大突发量）。</p>
     *
     * <h3>核心参数</h3>
     * <ul>
     *   <li><b>capacity</b>：桶的最大容量，决定允许的最大突发流量</li>
     *   <li><b>refillRate</b>：每秒补充的令牌数，决定稳态 QPS</li>
     * </ul>
     */
    public static class TokenBucket {

        /** 桶的最大容量（最大令牌数），默认 {@code 100} */
        private long capacity = 100;

        /** 每秒补充的令牌数（稳态 QPS），默认 {@code 10} */
        private double refillRate = 10;

        // ======================== Getter / Setter ========================

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public double getRefillRate() {
            return refillRate;
        }

        public void setRefillRate(double refillRate) {
            this.refillRate = refillRate;
        }
    }

    // ======================== 内部类：滑动窗口配置 ========================

    /**
     * 滑动窗口（Sliding Window）算法相关配置。
     *
     * <p>滑动窗口算法将时间划分为细粒度的时间片，统计当前窗口内的请求总数。
     * 与固定窗口不同，滑动窗口能更平滑地控制流量，避免窗口边界的突发问题。</p>
     *
     * <h3>核心参数</h3>
     * <ul>
     *   <li><b>windowSize</b>：窗口时间长度（秒），统计此范围内的请求</li>
     *   <li><b>maxPermits</b>：窗口内允许的最大请求数</li>
     * </ul>
     */
    public static class SlidingWindow {

        /** 窗口时间长度（秒），默认 {@code 60} */
        private long windowSize = 60;

        /** 窗口内允许的最大请求数，默认 {@code 100} */
        private long maxPermits = 100;

        // ======================== Getter / Setter ========================

        public long getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(long windowSize) {
            this.windowSize = windowSize;
        }

        public long getMaxPermits() {
            return maxPermits;
        }

        public void setMaxPermits(long maxPermits) {
            this.maxPermits = maxPermits;
        }
    }

    // ======================== 内部类：熔断器配置 ========================

    /**
     * 熔断器（Circuit Breaker）相关配置。
     *
     * <p>熔断器用于保护下游服务，当失败率达到阈值时自动熔断（快速失败），
     * 经过恢复超时后进入半开状态试探，成功后恢复关闭。</p>
     *
     * <h3>状态流转</h3>
     * <ol>
     *   <li><b>CLOSED（关闭）</b>：正常工作，统计失败率</li>
     *   <li><b>OPEN（开启）</b>：失败率超过阈值，拒绝所有请求</li>
     *   <li><b>HALF_OPEN（半开）</b>：恢复超时后，允许少量请求探测</li>
     *   <li>半开成功 → CLOSED；半开失败 → OPEN</li>
     * </ol>
     *
     * <h3>核心参数</h3>
     * <ul>
     *   <li><b>failureThreshold</b>：失败率阈值（0~1），超过此值触发熔断</li>
     *   <li><b>windowSize</b>：统计窗口大小（秒），在此窗口内计算失败率</li>
     *   <li><b>recoveryTimeout</b>：熔断恢复超时（秒），超过后可进入半开</li>
     *   <li><b>halfOpenMaxRequests</b>：半开状态下允许的最大试探请求数</li>
     * </ul>
     */
    public static class CircuitBreaker {

        /** 失败率阈值（0~1），超过此值触发熔断，默认 {@code 0.5} */
        private double failureThreshold = 0.5;

        /** 统计窗口大小（秒），默认 {@code 60} */
        private long windowSize = 60;

        /** 熔断恢复超时（秒），超过此时间后进入半开状态，默认 {@code 60} */
        private long recoveryTimeout = 60;

        /** 半开状态下允许的最大试探请求数，默认 {@code 3} */
        private int halfOpenMaxRequests = 3;

        // ======================== Getter / Setter ========================

        public double getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(double failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public long getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(long windowSize) {
            this.windowSize = windowSize;
        }

        public long getRecoveryTimeout() {
            return recoveryTimeout;
        }

        public void setRecoveryTimeout(long recoveryTimeout) {
            this.recoveryTimeout = recoveryTimeout;
        }

        public int getHalfOpenMaxRequests() {
            return halfOpenMaxRequests;
        }

        public void setHalfOpenMaxRequests(int halfOpenMaxRequests) {
            this.halfOpenMaxRequests = halfOpenMaxRequests;
        }
    }
}
