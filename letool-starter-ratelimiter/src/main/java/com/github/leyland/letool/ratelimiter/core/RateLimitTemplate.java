package com.github.leyland.letool.ratelimiter.core;

import com.github.leyland.letool.ratelimiter.config.RateLimiterProperties;
import com.github.leyland.letool.ratelimiter.exception.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 限流操作模板 —— 提供函数式编程风格的限流 API。
 *
 * <p>这是用户使用限流功能的<b>推荐入口</b>。相较于直接使用 {@link RateLimiter}
 * 接口手动管理许可获取和释放，本模板自动处理以下细节：</p>
 *
 * <ul>
 *   <li><b>自动获取许可</b>：根据配置的算法自动选择合适的限流器</li>
 *   <li><b>统一异常处理</b>：限流拒绝时抛出统一的 {@link RateLimitException}</li>
 *   <li><b>便捷 API</b>：提供多个重载方法，支持 Lambda 表达式、降级回退等</li>
 *   <li><b>Builder 模式</b>：支持链式调用构建复杂限流策略</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <p><b>1. 简单许可检查：</b></p>
 * <pre>{@code
 * if (rateLimitTemplate.tryAcquire("sms:138xxxx")) {
 *     sendSms();
 * } else {
 *     throw new RateLimitException("发送频率过高");
 * }
 * }</pre>
 *
 * <p><b>2. 带降级的执行：</b></p>
 * <pre>{@code
 * String result = rateLimitTemplate.executeOrFallback("order:123",
 *     () -> createOrder(),           // 正常执行
 *     () -> "系统繁忙，请稍后重试"    // 限流拒绝时的降级
 * );
 * }</pre>
 *
 * <p><b>3. Builder 模式：</b></p>
 * <pre>{@code
 * boolean allowed = rateLimitTemplate.builder()
 *         .key("sms:138xxxx")
 *         .permits(1)
 *         .tryAcquire();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see RateLimiter
 * @see RateLimitResult
 * @see RateLimitException
 */
public class RateLimitTemplate {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(RateLimitTemplate.class);

    // ======================== 依赖 ========================

    /** 底层限流器实现 */
    private final RateLimiter rateLimiter;

    /** 限流配置属性 */
    private final RateLimiterProperties properties;

    // ======================== 构造方法 ========================

    /**
     * 构造限流操作模板。
     *
     * @param rateLimiter 限流器实例（不可为 null）
     * @param properties  限流配置属性（不可为 null）
     */
    public RateLimitTemplate(RateLimiter rateLimiter, RateLimiterProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    // ======================== 许可检查：无回退 ========================

    /**
     * 尝试获取 1 个许可。
     *
     * <p>这是最简洁的调用方式，适用于大多数单次请求限流场景。</p>
     *
     * @param key 限流的唯一标识
     * @return {@code true} 允许通过，{@code false} 被限流
     */
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * 尝试获取指定数量的许可。
     *
     * <p>调用者应自行处理被拒绝的情况，如抛异常、返回错误码、或等待重试。</p>
     *
     * @param key     限流的唯一标识
     * @param permits 请求许可数
     * @return {@code true} 允许通过，{@code false} 被限流
     */
    public boolean tryAcquire(String key, int permits) {
        RateLimitResult result = rateLimiter.tryAcquire(key, permits);
        if (!result.isAllowed()) {
            log.warn("Rate limit rejected: key={}, permits={}, waitMs={}",
                    key, permits, result.getWaitTimeMs());
        }
        return result.isAllowed();
    }

    /**
     * 获取详细的限流结果（包含可用许可数和等待时间）。
     *
     * @param key     限流的唯一标识
     * @param permits 请求许可数
     * @return 限流结果，包含是否允许、可用许可数、预估等待时间
     */
    public RateLimitResult tryAcquireWithResult(String key, int permits) {
        return rateLimiter.tryAcquire(key, permits);
    }

    // ======================== 许可检查：带回退（Supplier） ========================

    /**
     * 执行带降级回退的操作 —— 有返回值版本。
     *
     * <p>如果获取许可成功，执行 {@code action} 并返回其结果；
     * 否则执行 {@code fallback} 并返回其结果。不会抛出异常。</p>
     *
     * <p>这是<b>推荐</b>的使用方式，因为它强制调用方考虑被限流时的降级行为。</p>
     *
     * @param <T>      返回值类型
     * @param key      限流的唯一标识
     * @param action   正常业务逻辑（获取许可成功时执行）
     * @param fallback 降级回退逻辑（被限流时执行）
     * @return 正常执行结果或降级回退结果
     */
    public <T> T executeOrFallback(String key, Supplier<T> action, Supplier<T> fallback) {
        return executeOrFallback(key, 1, action, fallback);
    }

    /**
     * 执行带降级回退的操作 —— 有返回值版本（指定许可数）。
     *
     * @param <T>      返回值类型
     * @param key      限流的唯一标识
     * @param permits  请求许可数
     * @param action   正常业务逻辑
     * @param fallback 降级回退逻辑
     * @return 正常执行结果或降级回退结果
     */
    public <T> T executeOrFallback(String key, int permits, Supplier<T> action, Supplier<T> fallback) {
        if (tryAcquire(key, permits)) {
            return action.get();
        }
        log.warn("Rate limit triggered, executing fallback: key={}, permits={}", key, permits);
        return fallback.get();
    }

    /**
     * 执行带降级回退的操作 —— 无返回值版本。
     *
     * @param key      限流的唯一标识
     * @param action   正常业务逻辑
     * @param fallback 降级回退逻辑
     */
    public void executeOrFallback(String key, Runnable action, Runnable fallback) {
        if (tryAcquire(key, 1)) {
            action.run();
        } else {
            log.warn("Rate limit triggered, executing fallback: key={}", key);
            fallback.run();
        }
    }

    // ======================== 许可检查：抛异常 ========================

    /**
     * 执行操作，被限流时抛出 {@link RateLimitException}。
     *
     * <p>适用于无法提供降级逻辑、希望由上层统一处理限流异常的场景。</p>
     *
     * @param <T>      返回值类型
     * @param key      限流的唯一标识
     * @param supplier 业务逻辑
     * @return 业务逻辑的返回值
     * @throws RateLimitException 当被限流拒绝时抛出
     */
    public <T> T executeOrThrow(String key, Supplier<T> supplier) {
        return executeOrThrow(key, 1, supplier);
    }

    /**
     * 执行操作，被限流时抛出 {@link RateLimitException}（指定许可数）。
     *
     * @param <T>      返回值类型
     * @param key      限流的唯一标识
     * @param permits  请求许可数
     * @param supplier 业务逻辑
     * @return 业务逻辑的返回值
     * @throws RateLimitException 当被限流拒绝时抛出
     */
    public <T> T executeOrThrow(String key, int permits, Supplier<T> supplier) {
        if (!tryAcquire(key, permits)) {
            throw new RateLimitException("Rate limit exceeded: key=" + key + ", permits=" + permits);
        }
        return supplier.get();
    }

    // ======================== Builder ========================

    /**
     * 创建限流操作的 Builder 实例，支持链式调用。
     *
     * <p>Builder 模式适合需要动态配置多个参数的场景，例如：</p>
     * <pre>{@code
     * rateLimitTemplate.builder()
     *         .key("sms:138xxxx")
     *         .permits(1)
     *         .execute(() -> sendSms());
     * }</pre>
     *
     * @return 新的 Builder 实例
     */
    public Builder builder() {
        return new Builder();
    }

    // ======================== 内部类：Builder ========================

    /**
     * 限流操作的 Builder。
     *
     * <p>提供链式 API 构建限流参数并执行。使用示例：</p>
     * <pre>{@code
     * rateLimitTemplate.builder()
     *         .key("sms:138xxxx")
     *         .permits(1)
     *         .tryAcquire();
     * }</pre>
     */
    public class Builder {

        private String key;
        private int permits = 1;

        /**
         * 设置限流的唯一标识。
         *
         * @param key 限流 key
         * @return 当前 Builder 实例
         */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /**
         * 设置请求许可数（默认 1）。
         *
         * @param permits 许可数
         * @return 当前 Builder 实例
         */
        public Builder permits(int permits) {
            this.permits = permits;
            return this;
        }

        /**
         * 尝试获取许可。
         *
         * @return {@code true} 允许通过，{@code false} 被限流
         * @throws IllegalStateException 如果未设置 key
         */
        public boolean tryAcquire() {
            validateKey();
            return RateLimitTemplate.this.tryAcquire(key, permits);
        }

        /**
         * 执行操作，被限流时执行降级回退。
         *
         * @param <T>      返回值类型
         * @param action   正常业务逻辑
         * @param fallback 降级回退逻辑
         * @return 正常执行结果或降级回退结果
         * @throws IllegalStateException 如果未设置 key
         */
        public <T> T executeOrFallback(Supplier<T> action, Supplier<T> fallback) {
            validateKey();
            return RateLimitTemplate.this.executeOrFallback(key, permits, action, fallback);
        }

        /**
         * 执行操作，被限流时抛出异常。
         *
         * @param <T>      返回值类型
         * @param supplier 业务逻辑
         * @return 业务逻辑的返回值
         * @throws RateLimitException      当被限流拒绝时抛出
         * @throws IllegalStateException  如果未设置 key
         */
        public <T> T executeOrThrow(Supplier<T> supplier) {
            validateKey();
            return RateLimitTemplate.this.executeOrThrow(key, permits, supplier);
        }

        /**
         * 校验 key 是否已设置。
         *
         * @throws IllegalStateException 如果 key 为 null
         */
        private void validateKey() {
            if (key == null) {
                throw new IllegalStateException("Rate limit key must not be null. Call .key() before executing.");
            }
        }
    }
}
