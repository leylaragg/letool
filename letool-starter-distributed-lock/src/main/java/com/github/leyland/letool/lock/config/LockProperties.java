package com.github.leyland.letool.lock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * letool-starter-distributed-lock 模块的全局配置属性类。
 *
 * <p>通过 Spring Boot 的 {@code @ConfigurationProperties} 机制绑定
 * {@code letool.lock} 前缀下的所有配置项，包含两个子模块的配置：</p>
 *
 * <ul>
 *   <li><b>Pessimistic（悲观锁）</b>：控制分布式锁的行为，包括锁的 key 前缀、
 *       默认持锁时间和默认等待时间；公平锁、自动续期字段为后续实现预留。</li>
 *   <li><b>Idempotent（幂等性）</b>：控制幂等检查的行为，包括是否启用、
 *       缓存 key 前缀、结果缓存时间等。</li>
 * </ul>
 *
 * <p>使用示例（application.yml）：</p>
 * <pre>{@code
 * letool:
 *   lock:
 *     enabled: true
 *     backend: redis
 *     pessimistic:
 *       lock-prefix: "myapp:lock:"
 *       default-lease-time: 60
 *       default-wait-time: 5
 *       auto-renewal: true
 *     idempotent:
 *       enabled: true
 *       key-prefix: "myapp:idempotent:"
 *       ttl: 7200
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "letool.lock")
public class LockProperties {

    // ======================== 顶层属性 ========================

    /** 全局开关：是否启用分布式锁模块，默认 {@code true} */
    private boolean enabled = true;

    /** 后端实现类型，当前仅支持 "redis"，默认 {@code "redis"} */
    private String backend = "redis";

    /** 悲观锁子配置 */
    private Pessimistic pessimistic = new Pessimistic();

    /** 幂等性子配置 */
    private Idempotent idempotent = new Idempotent();

    // ======================== Getter / Setter ========================

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public Pessimistic getPessimistic() { return pessimistic; }
    public void setPessimistic(Pessimistic pessimistic) { this.pessimistic = pessimistic; }
    public Idempotent getIdempotent() { return idempotent; }
    public void setIdempotent(Idempotent idempotent) { this.idempotent = idempotent; }

    // ======================== 内部类：悲观锁配置 ========================

    /**
     * 悲观锁（Pessimistic Lock）相关配置。
     *
     * <p>控制 Redis 分布式锁的运行时行为，包括锁的 key 命名规则和超时策略。
     * 公平性以及自动续期字段目前为后续实现预留。</p>
     */
    public static class Pessimistic {

        /** Redis 中锁 key 的前缀，默认 {@code "letool:lock:"} */
        private String lockPrefix = "letool:lock:";

        /** 默认持锁时间（秒），超过此时间锁自动释放，默认 {@code 30} */
        private long defaultLeaseTime = 30;

        /** 默认等待获取锁的最长时间（秒），超时返回失败，默认 {@code 3} */
        private long defaultWaitTime = 3;

        /** 是否启用公平锁（预留字段，当前内置 Redis 锁暂未实现公平队列），默认 {@code false} */
        private boolean fairLock = false;

        /** 是否启用自动续期（预留字段，当前内置 Redis 锁暂未执行自动续期），默认 {@code true} */
        private boolean autoRenewal = true;

        /** 自动续期间隔（秒，预留字段），默认 {@code 10} */
        private long renewalInterval = 10;

        // ======================== Getter / Setter ========================

        public String getLockPrefix() { return lockPrefix; }
        public void setLockPrefix(String lockPrefix) { this.lockPrefix = lockPrefix; }
        public long getDefaultLeaseTime() { return defaultLeaseTime; }
        public void setDefaultLeaseTime(long defaultLeaseTime) { this.defaultLeaseTime = defaultLeaseTime; }
        public long getDefaultWaitTime() { return defaultWaitTime; }
        public void setDefaultWaitTime(long defaultWaitTime) { this.defaultWaitTime = defaultWaitTime; }
        public boolean isFairLock() { return fairLock; }
        public void setFairLock(boolean fairLock) { this.fairLock = fairLock; }
        public boolean isAutoRenewal() { return autoRenewal; }
        public void setAutoRenewal(boolean autoRenewal) { this.autoRenewal = autoRenewal; }
        public long getRenewalInterval() { return renewalInterval; }
        public void setRenewalInterval(long renewalInterval) { this.renewalInterval = renewalInterval; }
    }

    // ======================== 内部类：幂等性配置 ========================

    /**
     * 幂等性（Idempotent）相关配置。
     *
     * <p>控制幂等检查的行为，通过 Redis 缓存请求标记来防止重复执行。</p>
     */
    public static class Idempotent {

        /** 是否启用幂等检查，默认 {@code true} */
        private boolean enabled = true;

        /** Redis 中幂等 key 的前缀，默认 {@code "letool:idempotent:"} */
        private String keyPrefix = "letool:idempotent:";

        /** 幂等标记的过期时间（秒），超过此时间允许重新执行，默认 {@code 86400}（24小时） */
        private long ttl = 86400;

        // ======================== Getter / Setter ========================

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
        public long getTtl() { return ttl; }
        public void setTtl(long ttl) { this.ttl = ttl; }
    }
}
