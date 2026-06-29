package com.github.leyland.letool.lock.core;

import com.github.leyland.letool.lock.config.LockProperties;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的悲观锁实现 —— 使用 Lua 脚本保证原子性。
 *
 * <h3>设计思路</h3>
 *
 * <p>相较于传统的 {@code SETNX + EXPIRE} 两步操作，本实现使用 <b>Lua 脚本</b>方式
 * 完成获取锁和释放锁，原因如下：</p>
 *
 * <ol>
 *   <li><b>原子性保证</b>：Lua 脚本在 Redis 服务端以原子方式执行，不会出现
 *       SETNX 成功后 EXPIRE 失败导致的死锁问题。</li>
 *   <li><b>安全释放</b>：释放锁时通过 token（UUID）校验，确保只有锁的持有者
 *       才能释放，防止误删其他线程的锁。</li>
 *   <li><b>单次网络往返</b>：获取锁（SET NX PX）和释放锁（GET + DEL）各只需
 *       一次 Redis 调用，减少网络开销。</li>
 * </ol>
 *
 * <h3>获取锁流程</h3>
 * <pre>{@code
 * ACQUIRE_SCRIPT = "SET lockKey token NX PX ttlMillis"
 * // NX: 仅当 key 不存在时设置（互斥语义）
 * // PX: 过期时间单位为毫秒（防止死锁）
 * }</pre>
 *
 * <h3>释放锁流程</h3>
 * <pre>{@code
 * RELEASE_SCRIPT = "if GET(lockKey) == token then DEL(lockKey) end"
 * // 先比较 token 是否匹配，匹配才删除（防止误删）
 * }</pre>
 *
 * <h3>自旋等待</h3>
 * <p>获取锁失败时以 50ms 间隔轮询重试，直到超过等待时间。不采用 Redis
 * 的发布/订阅通知，简化实现并避免额外的连接开销。</p>
 *
 * @author leyland
 * @since 1.0.0
 * @see DistributedLock
 */
public class RedisPessimisticLock implements DistributedLock {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(RedisPessimisticLock.class);

    // ======================== Lua 脚本 ========================

    /**
     * 获取锁的 Lua 脚本。
     * <p>KEYS[1] = 锁的完整 key，ARGV[1] = 持有者 token（UUID），ARGV[2] = 过期时间（毫秒）。
     * 返回 "OK" 表示获取成功，返回 nil 表示锁已被占用。</p>
     */
    private static final String ACQUIRE_SCRIPT = "return redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])";

    /**
     * 释放锁的 Lua 脚本。
     * <p>KEYS[1] = 锁的完整 key，ARGV[1] = 持有者 token。
     * 只有当 key 对应的值与 token 匹配时才执行 DEL，返回 1 表示删除成功，0 表示未删除。</p>
     */
    private static final String RELEASE_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";

    // ======================== 依赖与状态 ========================

    /** Redis 操作工具 */
    private final RedisUtil redisUtil;

    /** 配置属性 */
    private final LockProperties properties;

    /**
     * 本地持有的锁 token 映射表（key -> UUID token）。
     * <p>使用 ConcurrentHashMap 保证线程安全，仅用于本地追踪当前 JVM 持有的锁，
     * 实际的锁状态存储在 Redis 中。</p>
     */
    private final ConcurrentHashMap<String, String> lockTokens = new ConcurrentHashMap<>();

    // ======================== 构造方法 ========================

    /**
     * 构造 Redis 悲观锁实例。
     *
     * @param redisUtil  Redis 操作工具类（不可为 null）
     * @param properties 锁配置属性（不可为 null）
     */
    public RedisPessimisticLock(RedisUtil redisUtil, LockProperties properties) {
        this.redisUtil = redisUtil;
        this.properties = properties;
    }

    // ======================== 锁操作实现 ========================

    /**
     * 尝试获取分布式锁。
     *
     * <p>实现细节：</p>
     * <ol>
     *   <li>拼接完整锁 key（prefix + 业务 key）</li>
     *   <li>生成 UUID 作为当前持有者 token</li>
     *   <li>计算等待截止时间</li>
     *   <li>在截止时间内以 50ms 间隔自旋，每次执行 Lua SET NX PX 命令</li>
     *   <li>成功获取后，将 token 存入本地 {@code lockTokens} 映射</li>
     *   <li>超时或被中断则返回 {@code false}</li>
     * </ol>
     *
     * @param key       锁的唯一标识（业务 key，不含前缀）
     * @param waitTime  等待获取锁的最长时间
     * @param leaseTime 持锁租约时间（到期自动释放）
     * @param unit      时间单位
     * @return {@code true} 获取成功，{@code false} 超时或中断
     */
    @Override
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
        String lockKey = properties.getPessimistic().getLockPrefix() + key;
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + unit.toMillis(waitTime);

        while (System.currentTimeMillis() < deadline) {
            long ttlMillis = unit.toMillis(leaseTime);
            String result = redisUtil.executeScript(ACQUIRE_SCRIPT,
                    Collections.singletonList(lockKey), token, String.valueOf(ttlMillis));
            if ("OK".equals(result)) {
                lockTokens.put(key, token);
                return true;
            }
            // 自旋间隔 50ms，避免频繁请求 Redis
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 释放分布式锁。
     *
     * <p>从本地 {@code lockTokens} 中移出对应 key 的 token，然后通过 Lua 脚本
     * 在 Redis 端原子性地校验 token 并删除 key。如果本地没有该 key 的记录
     * （说明当前线程不持有该锁），则不做任何操作。</p>
     *
     * @param key 锁的唯一标识
     */
    @Override
    public void unlock(String key) {
        String lockKey = properties.getPessimistic().getLockPrefix() + key;
        String token = lockTokens.remove(key);
        if (token != null) {
            redisUtil.executeScript(RELEASE_SCRIPT, Collections.singletonList(lockKey), token);
        }
    }

    /**
     * 检查指定 key 是否处于锁定状态。
     *
     * <p>直接查询 Redis 中对应 key 是否存在，不做 token 校验。</p>
     *
     * @param key 锁的唯一标识
     * @return {@code true} 该 key 被锁定，{@code false} 未锁定
     */
    @Override
    public boolean isLocked(String key) {
        String lockKey = properties.getPessimistic().getLockPrefix() + key;
        String val = redisUtil.get(lockKey);
        return val != null;
    }
}
