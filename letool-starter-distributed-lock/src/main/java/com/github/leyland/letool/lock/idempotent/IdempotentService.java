package com.github.leyland.letool.lock.idempotent;

import com.github.leyland.letool.lock.config.LockProperties;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * 幂等性服务 —— 基于 Redis Lua 脚本的请求幂等性保证。
 *
 * <h3>核心机制</h3>
 *
 * <p>使用 <b>Redis SET NX EX</b> 命令的原子性实现"首次标记"语义：</p>
 * <ol>
 *   <li>尝试执行 {@code SET key "DONE" NX EX ttl}</li>
 *   <li>若返回 {@code "OK"}（即 key 之前不存在，SET 成功），说明是首次请求，
 *       执行 {@code supplier.get()} 获取结果并返回</li>
 *   <li>若返回 {@code nil}（即 key 已存在），说明是重复请求，记录日志并返回 {@code null}</li>
 * </ol>
 *
 * <p>采用 Lua 脚本而非两步操作（先 GET 再 SET）的原因：Lua 脚本在 Redis 服务端
 * 原子执行，避免了 GET 和 SET 之间的竞态条件。</p>
 *
 * <h3>异常处理</h3>
 *
 * <p>如果首次请求的业务逻辑抛出异常，幂等标记会被删除（回滚），
 * 允许后续请求重新尝试执行。这避免了因一次失败而永久阻塞后续请求。</p>
 *
 * @author leyland
 * @since 1.0.0
 * @see com.github.leyland.letool.lock.annotation.Idempotent
 * @see com.github.leyland.letool.lock.aspect.IdempotentAspect
 */
public class IdempotentService {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(IdempotentService.class);

    // ======================== Lua 脚本 ========================

    /**
     * 幂等标记 Lua 脚本。
     * <p>KEYS[1] = 幂等 key，ARGV[1] = 标记值 "DONE"，ARGV[2] = 过期时间（秒）。
     * 返回 "OK" 表示首次设置成功，返回 nil 表示 key 已存在（重复请求）。</p>
     */
    private static final String IDEMPOTENT_SCRIPT = "return redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])";

    /** 幂等标记值，表示操作已完成 */
    private static final String MARKER = "DONE";

    // ======================== 依赖 ========================

    /** Redis 操作工具 */
    private final RedisUtil redisUtil;

    /** 配置属性 */
    private final LockProperties properties;

    // ======================== 构造方法 ========================

    /**
     * 构造幂等服务。
     *
     * @param redisUtil  Redis 操作工具类（不可为 null）
     * @param properties 模块配置属性（不可为 null）
     */
    public IdempotentService(RedisUtil redisUtil, LockProperties properties) {
        this.redisUtil = redisUtil;
        this.properties = properties;
    }

    // ======================== 核心方法 ========================

    /**
     * 执行带幂等保证的操作。
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>拼接完整 Redis key（prefix + 业务 key）</li>
     *   <li>通过 Lua 脚本原子性地执行 SET NX EX</li>
     *   <li>若 SET 成功（首次请求）→ 执行 supplier 获取结果并返回</li>
     *   <li>若 SET 失败（重复请求）→ 记录日志并返回 {@code null}</li>
     *   <li>若 supplier 抛出异常 → 删除 Redis 中的幂等标记（回滚），重新抛出异常</li>
     * </ol>
     *
     * <p>调用方需自行处理返回 {@code null} 的情况（即重复请求）。</p>
     *
     * @param <T>        返回值类型
     * @param key        幂等校验的业务 key（不含前缀）
     * @param ttlSeconds 幂等标记的存活时间（秒），超时后允许重新执行
     * @param supplier   需要幂等保护的业务逻辑
     * @return 业务逻辑的返回值；重复请求时返回 {@code null}
     * @throws RuntimeException 业务逻辑执行异常（透传）
     */
    public <T> T execute(String key, long ttlSeconds, Supplier<T> supplier) {
        String redisKey = properties.getIdempotent().getKeyPrefix() + key;
        String result = redisUtil.executeScript(IDEMPOTENT_SCRIPT,
                Collections.singletonList(redisKey), MARKER, String.valueOf(ttlSeconds));
        if ("OK".equals(result)) {
            // 首次请求：执行业务逻辑
            try {
                return supplier.get();
            } catch (Exception e) {
                // 业务异常时回滚幂等标记，允许后续重试
                redisUtil.delete(redisKey);
                throw e;
            }
        } else {
            // 重复请求：直接返回 null
            log.info("Idempotent check: duplicate request for key={}", key);
            return null;
        }
    }
}
