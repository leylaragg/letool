package io.github.leylaragg.letool.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis Starter 的统一配置。
 *
 * <p>序列化、锁命名和缓存回源策略在此集中管理，业务代码只需要注入
 * {@code RedisFacade}，无需了解内部组件的装配关系。</p>
 */
@ConfigurationProperties(prefix = "letool.redis")
public class LetoolRedisProperties implements InitializingBean {

    private final Serialization serialization = new Serialization();
    private final Lock lock = new Lock();
    private final Idempotent idempotent = new Idempotent();
    private final Cache cache = new Cache();

    /** @return Redis 值序列化配置 */
    public Serialization getSerialization() {
        return serialization;
    }

    /** @return Redisson 锁配置 */
    public Lock getLock() {
        return lock;
    }

    /** @return Redis 幂等占位配置 */
    public Idempotent getIdempotent() {
        return idempotent;
    }

    /** @return 缓存回源配置 */
    public Cache getCache() {
        return cache;
    }

    /**
     * 在自动配置创建业务 Bean 前校验会影响 key 命名和过期语义的配置。
     */
    @Override
    public void afterPropertiesSet() {
        requireText(lock.keyPrefix, "letool.redis.lock.key-prefix");
        requireText(idempotent.keyPrefix, "letool.redis.idempotent.key-prefix");
        requireText(cache.lockKeyPrefix, "letool.redis.cache.lock-key-prefix");
        requirePositive(cache.nullTtl, "letool.redis.cache.null-ttl");
        requireNonNegative(cache.ttlJitter, "letool.redis.cache.ttl-jitter");
        requireNonNegative(cache.lockWait, "letool.redis.cache.lock-wait");
    }

    /** Redis 多态值的序列化安全边界。 */
    public static class Serialization {

        private List<String> autoTypeAcceptPrefixes = new ArrayList<>(
                List.of("io.github.leylaragg"));

        /** @return Fastjson2 自动类型允许的包名前缀 */
        public List<String> getAutoTypeAcceptPrefixes() {
            return autoTypeAcceptPrefixes;
        }

        /**
         * 替换自动类型允许的包名前缀。
         *
         * @param prefixes 业务确认安全的包名；{@code null} 表示不额外放行
         */
        public void setAutoTypeAcceptPrefixes(List<String> prefixes) {
            this.autoTypeAcceptPrefixes = prefixes == null
                    ? new ArrayList<>()
                    : new ArrayList<>(prefixes);
        }
    }

    /** Redisson 锁的命名和类型策略。 */
    public static class Lock {

        private String keyPrefix = "letool:lock:";
        private boolean fair;

        /** @return 写入 Redis 的锁 key 前缀 */
        public String getKeyPrefix() {
            return keyPrefix;
        }

        /** @param keyPrefix 写入 Redis 的锁 key 前缀 */
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        /** @return 是否使用 Redisson 公平锁 */
        public boolean isFair() {
            return fair;
        }

        /** @param fair {@code true} 时使用公平锁 */
        public void setFair(boolean fair) {
            this.fair = fair;
        }
    }

    /** Redis 幂等占位的命名策略。 */
    public static class Idempotent {

        private String keyPrefix = "letool:idempotent:";

        /** @return 写入 Redis 的幂等 key 前缀 */
        public String getKeyPrefix() {
            return keyPrefix;
        }

        /** @param keyPrefix 写入 Redis 的幂等 key 前缀 */
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    /** 单值缓存回源的默认保护策略。 */
    public static class Cache {

        private boolean cacheNull = true;
        private Duration nullTtl = Duration.ofMinutes(2);
        private Duration ttlJitter = Duration.ofSeconds(30);
        private Duration lockWait = Duration.ofSeconds(3);
        private String lockKeyPrefix = "cache:";

        /** @return 数据源返回空值时是否写入短期标记 */
        public boolean isCacheNull() {
            return cacheNull;
        }

        /** @param cacheNull 是否缓存数据源空值 */
        public void setCacheNull(boolean cacheNull) {
            this.cacheNull = cacheNull;
        }

        /** @return 空值标记的存活时间 */
        public Duration getNullTtl() {
            return nullTtl;
        }

        /** @param nullTtl 空值标记的存活时间 */
        public void setNullTtl(Duration nullTtl) {
            this.nullTtl = nullTtl;
        }

        /** @return 正常缓存 TTL 的最大随机抖动 */
        public Duration getTtlJitter() {
            return ttlJitter;
        }

        /** @param ttlJitter 正常缓存 TTL 的最大随机抖动 */
        public void setTtlJitter(Duration ttlJitter) {
            this.ttlJitter = ttlJitter;
        }

        /** @return 等待缓存回源锁的最长时间 */
        public Duration getLockWait() {
            return lockWait;
        }

        /** @param lockWait 等待缓存回源锁的最长时间 */
        public void setLockWait(Duration lockWait) {
            this.lockWait = lockWait;
        }

        /** @return 拼接业务缓存 key 前使用的锁 key 前缀 */
        public String getLockKeyPrefix() {
            return lockKeyPrefix;
        }

        /** @param lockKeyPrefix 缓存回源锁的业务前缀 */
        public void setLockKeyPrefix(String lockKeyPrefix) {
            this.lockKeyPrefix = lockKeyPrefix;
        }
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " 不能为空");
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(property + " 必须大于零");
        }
    }

    private static void requireNonNegative(Duration value, String property) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(property + " 不能为负数");
        }
    }
}
