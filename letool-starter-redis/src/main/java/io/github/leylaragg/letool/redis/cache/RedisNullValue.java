package io.github.leylaragg.letool.redis.cache;

/**
 * {@code getOrLoad} 内部使用的空值哨兵。
 *
 * <p>使用独立类型而不是魔法字符串，避免与真实业务值冲突。</p>
 */
public enum RedisNullValue {
    /** 表示数据源已确认不存在。 */
    INSTANCE
}
