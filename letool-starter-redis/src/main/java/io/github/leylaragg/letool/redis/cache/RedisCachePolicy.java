package io.github.leylaragg.letool.redis.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 一次 {@code getOrLoad} 调用的不可变缓存策略。
 *
 * @param <T> 数据源返回值类型
 */
public final class RedisCachePolicy<T> {

    private final Duration ttl;
    private final boolean cacheNull;
    private final Duration nullTtl;
    private final Duration ttlJitter;
    private final Duration lockWait;
    private final Predicate<T> cacheable;

    private RedisCachePolicy(Builder<T> builder) {
        this.ttl = builder.ttl;
        this.cacheNull = builder.cacheNull;
        this.nullTtl = builder.nullTtl;
        this.ttlJitter = builder.ttlJitter;
        this.lockWait = builder.lockWait;
        this.cacheable = builder.cacheable;
    }

    /**
     * 以正常数据 TTL 创建策略构建器。
     *
     * @param ttl 正常数据 TTL
     * @param <T> 数据类型
     * @return 策略构建器
     */
    public static <T> Builder<T> builder(Duration ttl) {
        return new Builder<>(ttl);
    }

    /** @return 正常数据 TTL */
    public Duration ttl() {
        return ttl;
    }

    /** @return 是否缓存数据源空值 */
    public boolean cacheNull() {
        return cacheNull;
    }

    /** @return 空值哨兵 TTL */
    public Duration nullTtl() {
        return nullTtl;
    }

    /** @return 正常数据 TTL 的最大随机抖动 */
    public Duration ttlJitter() {
        return ttlJitter;
    }

    /** @return 等待缓存重建锁的最长时间 */
    public Duration lockWait() {
        return lockWait;
    }

    /** @return 判断非空数据是否允许写缓存的谓词 */
    public Predicate<T> cacheable() {
        return cacheable;
    }

    /** 构建缓存回源策略。 */
    public static final class Builder<T> {

        private final Duration ttl;
        private boolean cacheNull = true;
        private Duration nullTtl = Duration.ofMinutes(2);
        private Duration ttlJitter = Duration.ZERO;
        private Duration lockWait = Duration.ofSeconds(3);
        private Predicate<T> cacheable = value -> true;

        private Builder(Duration ttl) {
            this.ttl = requirePositive(ttl, "ttl");
        }

        /**
         * 启用空值缓存并设置哨兵 TTL。
         *
         * @param nullTtl 空值哨兵 TTL
         * @return 当前构建器
         */
        public Builder<T> cacheNull(Duration nullTtl) {
            this.cacheNull = true;
            this.nullTtl = requirePositive(nullTtl, "nullTtl");
            return this;
        }

        /** @return 禁止缓存空值后的当前构建器 */
        public Builder<T> doNotCacheNull() {
            this.cacheNull = false;
            return this;
        }

        /**
         * 设置追加到正常 TTL 的最大随机抖动。
         *
         * @param ttlJitter 最大 TTL 抖动，允许为零
         * @return 当前构建器
         */
        public Builder<T> ttlJitter(Duration ttlJitter) {
            this.ttlJitter = requireNonNegative(ttlJitter, "ttlJitter");
            return this;
        }

        /**
         * 设置等待缓存重建锁的最长时间。
         *
         * @param lockWait 锁等待时间，允许为零
         * @return 当前构建器
         */
        public Builder<T> lockWait(Duration lockWait) {
            this.lockWait = requireNonNegative(lockWait, "lockWait");
            return this;
        }

        /**
         * 设置非空数据写入缓存前的业务判断。
         *
         * @param cacheable 非空结果的缓存条件
         * @return 当前构建器
         */
        public Builder<T> cacheable(Predicate<T> cacheable) {
            this.cacheable = Objects.requireNonNull(cacheable, "cacheable must not be null");
            return this;
        }

        /** @return 完成校验的不可变策略 */
        public RedisCachePolicy<T> build() {
            return new RedisCachePolicy<>(this);
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " 必须大于零");
        }
        return value;
    }

    private static Duration requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " 不能为负数");
        }
        return value;
    }
}
